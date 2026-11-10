from contextlib import asynccontextmanager
import asyncio
import json
import logging
import secrets
import sqlite3
import traceback
from typing import Annotated
from weakref import WeakValueDictionary

import aiosqlite
from fastapi import Depends, FastAPI, Header, Request, Response
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
import httpx
from langchain_core.exceptions import OutputParserException
from langgraph.checkpoint.serde.jsonplus import JsonPlusSerializer
from langgraph.checkpoint.sqlite.aio import AsyncSqliteSaver
from langgraph.errors import GraphRecursionError
from langgraph.types import Command
import openai
from pydantic import ValidationError

from app.config import Settings
from app.gateway import AgentError, JavaGateway
from app.graph import RunContext, build_graph, initial_state
from app.schemas import AgentResult, ResumeRequest, RunRequest, TaskContext, TaskId, TaskSpec


logger = logging.getLogger("health_agent")
settings = Settings.from_env()


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings.checkpoint_path.parent.mkdir(parents=True, exist_ok=True)
    # Official async saver; serializer permits built-in LangGraph/LangChain types
    # while disallowing arbitrary module constructors and pickle.
    # https://reference.langchain.com/python/langgraph.checkpoint.sqlite/aio/AsyncSqliteSaver
    async with aiosqlite.connect(str(settings.checkpoint_path)) as connection:
        saver = AsyncSqliteSaver(connection, serde=JsonPlusSerializer(
            pickle_fallback=False, allowed_json_modules=None, allowed_msgpack_modules=None,
        ))
        await saver.setup()
        async with httpx.AsyncClient(
            base_url=settings.java_api_url,
            headers={"X-Service-Key": settings.service_key},
            timeout=httpx.Timeout(20.0, connect=5.0),
        ) as java_client:
            app.state.java_client = java_client
            app.state.saver = saver
            app.state.graph = build_graph(settings, saver)
            app.state.locks = WeakValueDictionary()
            app.state.running = {}
            yield


app = FastAPI(
    title="Sleep Health Agent", version="0.1.0", lifespan=lifespan,
    docs_url=None, redoc_url=None, openapi_url=None,
)


def error_response(status_code: int, code: str, message: str) -> JSONResponse:
    return JSONResponse(status_code=status_code, content={"code": code, "message": message})


def log_failure(request: Request, exc: Exception) -> None:
    # Record code locations, not exception text that can contain raw model output.
    locations = " -> ".join(
        f"{frame.filename}:{frame.lineno}({frame.name})"
        for frame in traceback.extract_tb(exc.__traceback__)
    )
    logger.error("%s at %s; %s", type(exc).__name__, request.url.path, locations)


async def require_service_key(x_service_key: Annotated[str | None, Header()] = None) -> None:
    if not settings.service_key:
        raise AgentError(503, "SERVICE_KEY_NOT_CONFIGURED", "请配置INTERNAL_SERVICE_KEY后启动内部任务")
    if x_service_key is None or not secrets.compare_digest(x_service_key.encode(), settings.service_key.encode()):
        raise AgentError(403, "SERVICE_AUTH_FAILED", "内部服务认证失败")


def require_model() -> None:
    if not settings.model_configured:
        raise AgentError(503, "MODEL_NOT_CONFIGURED", "请配置OPENAI_API_KEY、OPENAI_BASE_URL、OPENAI_MODEL后手动重试")


def graph_config(task_id: str) -> dict:
    return {"configurable": {"thread_id": task_id}, "recursion_limit": 48}


def task_spec(context: TaskContext) -> dict:
    return context.model_dump(mode="json", include=set(TaskSpec.model_fields))


@asynccontextmanager
async def active_run(app: FastAPI, task_id: str):
    lock = app.state.locks.setdefault(task_id, asyncio.Lock())
    async with lock:
        app.state.running[task_id] = asyncio.current_task()
        try:
            yield
        finally:
            app.state.running.pop(task_id)


@app.get("/health")
async def health() -> dict:
    return {"status": "ok", "modelConfigured": settings.model_configured}


@app.post("/internal/runs", response_model=AgentResult, dependencies=[Depends(require_service_key)])
async def start_run(payload: RunRequest, request: Request) -> dict:
    require_model()
    async with active_run(request.app, payload.taskId):
        gateway = JavaGateway(request.app.state.java_client, payload.taskId, payload.taskToken)
        context = await gateway.authorized_context()
        config = graph_config(payload.taskId)
        graph = request.app.state.graph
        snapshot = await graph.aget_state(config)
        if snapshot.values:
            raise AgentError(409, "RUN_ALREADY_EXISTS", "任务检查点已存在，请使用继续或手动重试接口")
        spec = task_spec(context)
        output = await graph.ainvoke(
            initial_state(spec), config=config, context=RunContext(gateway, spec), durability="sync",
        )
        return output["result"]


@app.post("/internal/runs/{task_id}/resume", response_model=AgentResult, dependencies=[Depends(require_service_key)])
async def resume_run(task_id: TaskId, payload: ResumeRequest, request: Request) -> dict:
    async with active_run(request.app, task_id):
        gateway = JavaGateway(request.app.state.java_client, task_id, payload.taskToken)
        context = await gateway.context() if payload.action == "CONFIRM" else await gateway.authorized_context()
        config = graph_config(task_id)
        graph = request.app.state.graph
        snapshot = await graph.aget_state(config)
        spec = task_spec(context)
        if payload.action == "CONFIRM":
            if context.status != "COMPLETED" or context.planId != payload.planId:
                raise AgentError(409, "PLAN_NOT_COMMITTED", "Java尚未保存匹配的正式计划，不能关闭确认节点")
            if not snapshot.next and snapshot.values.get("result", {}).get("status") == "COMPLETED":
                return snapshot.values["result"]
            if not snapshot.interrupts or snapshot.values["result"]["status"] != "WAITING_CONFIRMATION":
                raise AgentError(409, "RUN_NOT_WAITING_CONFIRMATION", "检查点不处于待确认状态")
            command = Command(resume={"action": "CONFIRM", "planId": payload.planId})
        elif payload.action == "INPUT":
            require_model()
            if not snapshot.interrupts or snapshot.values["result"]["status"] != "WAITING_INPUT":
                raise AgentError(409, "RUN_NOT_WAITING_INPUT", "检查点不处于待补充状态")
            command = Command(resume={"action": "INPUT", "input": payload.input})
        else:
            require_model()
            if snapshot.interrupts or (snapshot.values and not snapshot.next):
                raise AgentError(409, "RUN_NOT_FAILED", "该检查点不能手动重试")
            # Configuration/connection failure can occur before the first checkpoint.
            # Explicit RETRY reconstructs that same task from Java's authoritative scope.
            command = None if snapshot.values else initial_state(spec)
        output = await graph.ainvoke(
            command, config=config, context=RunContext(gateway, spec), durability="sync",
        )
        return output["result"]


@app.delete("/internal/runs/{task_id}", status_code=204, dependencies=[Depends(require_service_key)])
async def delete_run(task_id: TaskId, request: Request) -> Response:
    running = request.app.state.running.get(task_id)
    if running is not None:
        # A privacy deletion stops the graph before erasing its checkpoints;
        # waiting for its normal model timeout would pin Java's delete transaction.
        running.cancel()
        await asyncio.gather(running, return_exceptions=True)
    lock = request.app.state.locks.setdefault(task_id, asyncio.Lock())
    async with lock:
        await request.app.state.saver.adelete_thread(task_id)
    return Response(status_code=204)


@app.exception_handler(AgentError)
async def agent_error_handler(request: Request, exc: AgentError) -> JSONResponse:
    return error_response(exc.status_code, exc.code, exc.message)


@app.exception_handler(RequestValidationError)
async def request_error_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
    # Do not echo request bodies: taskToken is present in the input DTO.
    details = "; ".join(f"{'.'.join(map(str, error['loc']))}: {error['msg']}" for error in exc.errors())
    return error_response(422, "INVALID_REQUEST", details)


@app.exception_handler(ValidationError)
@app.exception_handler(OutputParserException)
async def schema_error_handler(request: Request, exc: Exception) -> JSONResponse:
    log_failure(request, exc)
    return error_response(502, "UPSTREAM_SCHEMA_INVALID", "模型或Java工具返回的数据不符合接口约定，请检查模型结构化输出能力和服务日志")


@app.exception_handler(httpx.HTTPStatusError)
async def java_status_error_handler(request: Request, exc: httpx.HTTPStatusError) -> JSONResponse:
    status = exc.response.status_code
    # Java's documented errors are {code,message}; a non-JSON proxy error is
    # reported explicitly as an upstream HTTP failure, never as an empty result.
    try:
        body = exc.response.json()
    except json.JSONDecodeError:
        return error_response(502, "JAVA_HTTP_ERROR", f"Java接口返回HTTP {status}且错误响应不是JSON")
    return error_response(status if status < 500 else 502, body["code"], body["message"])


@app.exception_handler(httpx.TimeoutException)
async def java_timeout_handler(request: Request, exc: httpx.TimeoutException) -> JSONResponse:
    return error_response(504, "JAVA_TIMEOUT", "Java工具请求超时，请检查Java服务后手动重试")


@app.exception_handler(httpx.RequestError)
async def java_connection_handler(request: Request, exc: httpx.RequestError) -> JSONResponse:
    return error_response(502, "JAVA_CONNECTION_FAILED", "无法连接Java工具服务，请检查JAVA_API_URL及服务状态后手动重试")


@app.exception_handler(openai.APITimeoutError)
async def model_timeout_handler(request: Request, exc: openai.APITimeoutError) -> JSONResponse:
    return error_response(504, "MODEL_TIMEOUT", "外部模型请求超时，请检查模型服务后手动重试")


@app.exception_handler(openai.APIConnectionError)
async def model_connection_handler(request: Request, exc: openai.APIConnectionError) -> JSONResponse:
    return error_response(502, "MODEL_CONNECTION_FAILED", "外部模型连接失败，请检查OPENAI_BASE_URL和网络后手动重试")


@app.exception_handler(openai.APIStatusError)
async def model_status_handler(request: Request, exc: openai.APIStatusError) -> JSONResponse:
    status = exc.status_code
    if status in (401, 403):
        return error_response(403, "MODEL_AUTH_FAILED", "模型服务认证或权限失败，请修正模型凭证")
    if status in (400, 404, 422):
        return error_response(422, "MODEL_REQUEST_INVALID", f"模型服务拒绝请求（HTTP {status}），请检查模型名称和工具调用支持")
    if status == 429:
        return error_response(429, "MODEL_RATE_LIMITED", "模型服务限流或额度不足，请处理后手动重试")
    return error_response(502, "MODEL_HTTP_ERROR", f"模型服务返回HTTP {status}，请检查外部模型服务后手动重试")


@app.exception_handler(GraphRecursionError)
async def graph_limit_handler(request: Request, exc: GraphRecursionError) -> JSONResponse:
    return error_response(422, "MODEL_STEP_LIMIT", "任务超过图执行轮数上限，请缩小目标后新建任务")


@app.exception_handler(sqlite3.Error)
async def checkpoint_error_handler(request: Request, exc: sqlite3.Error) -> JSONResponse:
    log_failure(request, exc)
    return error_response(500, "CHECKPOINT_STORAGE_FAILED", "检查点读写失败，请检查CHECKPOINT_PATH及磁盘权限")


@app.exception_handler(Exception)
async def unexpected_error_handler(request: Request, exc: Exception) -> JSONResponse:
    log_failure(request, exc)
    return error_response(500, "AGENT_EXECUTION_FAILED", f"Agent执行失败（{type(exc).__name__}），请检查服务日志")

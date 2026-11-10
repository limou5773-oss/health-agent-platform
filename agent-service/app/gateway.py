from dataclasses import dataclass, field
from typing import Any

import httpx
from pydantic import TypeAdapter

from app.schemas import Article, EmptyArguments, KnowledgeArguments, SleepStatistics, TaskContext


class AgentError(Exception):
    def __init__(self, status_code: int, code: str, message: str):
        super().__init__(message)
        self.status_code = status_code
        self.code = code
        self.message = message


class SourceSelectionRequired(AgentError):
    def __init__(self):
        super().__init__(409, "SOURCE_SELECTION_REQUIRED", "该日期范围包含多个来源，请选择一个来源后继续")


@dataclass(frozen=True)
class JavaGateway:
    client: httpx.AsyncClient = field(repr=False)
    task_id: str
    task_token: str = field(repr=False)

    async def request(self, method: str, suffix: str, **kwargs: Any) -> Any:
        response = await self.client.request(
            method,
            f"/internal/agent/tasks/{self.task_id}/{suffix}",
            headers={"X-Task-Token": self.task_token},
            **kwargs,
        )
        if response.status_code == 409 and response.json().get("code") == "SOURCE_SELECTION_REQUIRED":
            raise SourceSelectionRequired()
        response.raise_for_status()
        return response.json()

    async def context(self) -> TaskContext:
        return TaskContext.model_validate(await self.request("GET", "context"))

    async def authorized_context(self) -> TaskContext:
        context = await self.context()
        if not context.aiConsent:
            raise AgentError(403, "AI_CONSENT_REQUIRED", "用户已撤销AI授权，任务不能继续")
        if context.status == "CANCELLED":
            raise AgentError(409, "TASK_CANCELLED", "任务已取消")
        return context

    async def step(self, name: str, input_summary: str, output_summary: str, status: str = "COMPLETED") -> None:
        await self.request(
            "POST",
            "steps",
            json={
                "name": name,
                "status": status,
                "inputSummary": input_summary,
                "outputSummary": output_summary,
            },
        )

    async def execute_tool(self, name: str, arguments: dict) -> tuple[Any, str]:
        # Tool arguments are an external model boundary. Identity is never an argument.
        if name == "search_knowledge":
            parsed = KnowledgeArguments.model_validate(arguments)
            articles = TypeAdapter(list[Article]).validate_python(
                await self.request("GET", "tools/knowledge", params={"query": parsed.query})
            )
            return [article.model_dump(mode="json") for article in articles], f"检索到{len(articles)}条资料"
        EmptyArguments.model_validate(arguments)
        if name == "get_sleep_statistics":
            statistics = SleepStatistics.model_validate(await self.request("GET", "tools/sleep-statistics"))
            return statistics.model_dump(mode="json"), f"有效{statistics.validDays}/{statistics.periodDays}天，共{statistics.recordCount}条记录"
        if name == "get_profile":
            return await self.request("GET", "tools/profile"), "已读取当前任务用户的健康档案"
        if name == "get_plans":
            plans = await self.request("GET", "tools/plans")
            return plans, f"读取到{len(plans)}份已有计划"
        raise AgentError(502, "MODEL_TOOL_INVALID", f"模型请求了不支持的工具：{name[:80]}")


def tool_schema(name: str, description: str, arguments: type) -> dict:
    return {"type": "function", "function": {
        "name": name, "description": description, "parameters": arguments.model_json_schema(),
    }}


TOOLS = [
    tool_schema("get_profile", "读取当前任务用户授权的健康档案和偏好，无需提供用户身份。", EmptyArguments),
    tool_schema("get_sleep_statistics", "查询任务保存的分析日期与来源的真实睡眠统计；缺失日为null，不能自行编造或扩大日期范围。", EmptyArguments),
    tool_schema("search_knowledge", "按关键词检索已发布健康知识，引用仅使用真实返回的资料编号。", KnowledgeArguments),
    tool_schema("get_plans", "查询当前任务用户的已有计划概要，避免制定重复或冲突计划。", EmptyArguments),
]

# Python Agent 服务

Python 3.13、FastAPI、LangGraph 单进程服务。接口以 `../docs/api-contract.md` 为准。Java负责用户身份、业务写入和任务归属，Python仅通过带任务令牌的Java只读工具读取授权数据。

## 安装与启动

在本目录执行；根目录启动脚本会加载统一 `.env`，单独启动时先在当前终端设置对应环境变量。

```powershell
& 'D:\IT\python13.1\python.exe' -m venv .venv
& '.venv\Scripts\python.exe' -m pip install --only-binary=:all: -r requirements.lock.txt
& '.venv\Scripts\python.exe' -m uvicorn app.main:app --host 127.0.0.1 --port 8001 --workers 1
```

`requirements.txt` 记录直接依赖，`requirements.lock.txt` 固定本次安装的完整依赖版本。不得多进程共用本地检查点文件。

| 环境变量 | 用途 |
| --- | --- |
| `JAVA_API_URL` | Java接口根地址，默认 `http://127.0.0.1:8080` |
| `INTERNAL_SERVICE_KEY` | Java/Python共享内部认证密钥，必填 |
| `OPENAI_API_KEY` | 外部模型凭证，必填，不放入模型提示词或检查点状态 |
| `OPENAI_BASE_URL` | 标准OpenAI兼容接口地址，必填，例如供应商提供的 `/v1` 地址 |
| `OPENAI_MODEL` | 供应商实际提供的模型名称，必填 |
| `CHECKPOINT_PATH` | SQLite文件，默认 `./data/checkpoints.sqlite`，包含会话健康数据，应限制本机文件访问 |

模型必须支持标准Chat Completions工具调用，包括强制调用指定结构化输出函数。服务不对供应商扩展字段做兼容回退，不自动更换模型或返回预设健康结果。三项模型配置任一缺失，`/health` 返回 `modelConfigured:false`，执行返回503。该标记表示配置存在，不代表已联网验证凭证或供应商能力。

## 执行与恢复

- 模型在 `get_profile`、`get_sleep_statistics`、`search_knowledge`、`get_plans` 中自主选择工具。工具实际访问Java，不允许模型传入用户身份、令牌、任意URL或扩展统计范围。
- 模型与工具在LangGraph节点中循环；每轮模型最长90秒，最多12轮决策。Java工具请求最长20秒。所有调用均不自动重试，用户通过Java任务接口手动重试。
- 输出通过Pydantic结构化解析一次；统计来自真实统计工具，报告引用元数据来自实际检索资料。计划执行日期使用独立的 `planStartDate/planEndDate`，最多31天和93项。
- 缺少数据或偏好时通过 `interrupt` 等待输入。多来源冲突是明确可恢复的业务分支，产生 `WAITING_INPUT`，Java先保存用户选定来源，再用 `Command(resume=...)` 继续并重新查询统计。
- 计划草稿通过 `interrupt` 等待确认。Java事务保存正式计划并设置任务完成后，Python以匹配的 `planId` 恢复并闭合图；该节点不再调用模型、不重复写入正式计划。草稿版本由Java管理。
- 检查点持久化于官方 `AsyncSqliteSaver`。使用安全类型允许列表，不允许任意构造器或pickle。服务重启后使用相同任务ID、检查点文件和Java任务令牌继续。
- 同一任务的执行与恢复串行进行。Java删除任务时调用Python的DELETE接口，先取消并等待正在执行的图退出，再清理全部检查点，避免等待完整模型超时才删除。普通取消通过Java任务状态和令牌阻止后续工具调用。
- `/steps` 仅记录工具名称、状态、记录数量等操作摘要，不保存模型内部推理、令牌或完整健康文件。任务HTTP边界返回 `{code,message}` 真正错误；Java负责将任务标为失败。

## 本次校验范围

按项目要求只执行源码编译、模块导入、LangGraph图编译和依赖一致性检查；不编写或运行功能/回归测试，不调用真实模型或健康业务接口。启动和完整业务链路由用户自行测试。

官方实现依据：[LangGraph工具循环](https://docs.langchain.com/oss/python/langgraph/quickstart)、[中断与恢复](https://docs.langchain.com/oss/python/langgraph/interrupts)、[运行时上下文](https://docs.langchain.com/oss/python/langgraph/graph-api#runtime-context)、[AsyncSqliteSaver](https://reference.langchain.com/python/langgraph.checkpoint.sqlite/aio/AsyncSqliteSaver)、[ChatOpenAI结构化输出](https://reference.langchain.com/python/langchain-openai/chat_models/base/ChatOpenAI/with_structured_output)。

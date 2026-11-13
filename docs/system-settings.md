# 提醒与系统配置

对应 AD06。管理员在「管理后台 → 系统配置」维护平台级规则。打卡、计划到期、报告、体重变化、导入失败、周度复盘、月度复盘和导入后分析可分别开启或关闭。平台开关与用户设置同时生效，自动分析还须有当前 AI 授权。已有任务和消息保留，关闭后停止对应规则的新触发。

用户在「健康助手 → 主动任务设置与记录」可分别选择周度和月度复盘。周度取最近完整自然周，月度取上一个完整自然月，按档案时区计算。原有周度设置继续有效，月度默认关闭。

模型连接仍使用 Agent 服务端的 `OPENAI_BASE_URL` 和 `OPENAI_API_KEY`。管理员可覆盖模型名称并设置 5 至 90 秒的单次模型请求超时。留空使用部署的 `OPENAI_MODEL`。保存后的值在任务新建执行或恢复时读取。连接检查使用表单当前值发送固定探测文本，返回连接、认证、超时或模型等错误，不返回远端错误正文。检查成功表示该模型接受了探测请求。

## 接口与升级

- `GET /api/admin/system-settings` 查询配置，`PUT /api/admin/system-settings` 保存配置。
- `POST /api/admin/system-settings/model-check` 接收 `{modelName,modelTimeoutSeconds}`，成功返回 `{connected:true}`，失败返回错误码和提示。
- 现有主动任务设置新增 `monthlyEnabled`，触发历史增加 `MONTHLY_REVIEW`。

已有库执行 `database/migrations/017_system_settings.sql`，新库使用最新 `database/schema.sql`。同步更新 Java、Python 和前端。平台规则初始开启，用户仍须自行启用提醒或主动任务。

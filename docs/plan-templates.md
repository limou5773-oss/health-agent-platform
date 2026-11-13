# 计划模板管理

对应 AD05。管理员在「管理后台 → 计划模板」新增或编辑睡眠、运动和饮水模板，设置适用说明、周期、任务名称、内容、执行时间和相对天数。周期为 1 至 31 天，任务为 1 至 93 项。新增从第 1 版开始，每次保存递增版本。关闭「允许用户使用」后，该模板不再接受新草稿。

登录用户在「健康助手 → 从计划模板创建草稿」选择启用的模板及开始日期。Java 将相对天数转为实际日期，将模板内容复制到待确认草稿，并保存模板编号和版本。生成时检查模板仍启用且版本相符。编辑、确认、取消和任务追溯沿用已有流程。既有草稿及正式计划保留生成时的内容。

模板生成是本地操作，不读取健康记录、不调用外部模型，也不建立 Agent 检查点。草稿由用户确认后保存为正式计划。

## 接口与升级

- `GET /api/admin/plan-templates`、`POST /api/admin/plan-templates`、`PUT /api/admin/plan-templates/{id}` 查询和保存模板。保存字段为 `version`、`category`（`SLEEP`、`EXERCISE`、`WATER`）、`title`、`instructions`、`days`、`enabled`、`items`（每项为 `day`、`time`、`title`、`description`）。新建提交版本 0，编辑提交读取到的当前版本。
- `GET /api/plan-templates` 返回启用模板。
- `POST /api/plan-templates/{id}/draft` 接收 `{version,startDate}`，返回待确认任务。

已有库执行 `database/migrations/016_plan_templates.sql`，新库使用最新 `database/schema.sql`。管理接口沿用 JWT 管理员校验，任务与模板按编号逻辑关联。

# 站内提醒与消息中心

对应 US15。在「消息中心」查看消息、筛选未读、单条或批量标为已读，并跳转到计划、报告、体重记录或导入入口。提醒设置按类别保存，未设置时五类均关闭。关闭某类后停止新增，已有消息可以继续查看。

| 类型 | 触发和频率 |
| --- | --- |
| 打卡提醒 | 按档案时区检查当天进行中的计划。仅提醒未打卡且仍有效的任务。按任务提醒优先用修订保存的提醒时间，未设置时用任务执行时间。也可选每天在设定时间汇总一次。 |
| 计划到期 | 进行中的计划在结束日期当天提醒，每个计划及结束日期一次。 |
| 报告生成 | 睡眠报告、综合周报及月报保存成功时各发送一次，跳转到对应报告。 |
| 数据变化 | 对比同一来源最近两条已有体重记录。最新测量发生在当天且数值不同时提醒，每对记录一次，并显示最新测量时间作为数据截止时间。消息只描述观测变化。 |
| 导入失败 | 睡眠和步数预览批次存在校验失败或不支持的行时按批次发送。文件预览被业务校验拒绝且没有批次时，按该次上传尝试发送。 |

定时检查每分钟运行一次，处理档案时区内当天的事件。后端关闭期间的往日提醒不补发。计划暂停、结束或任务被修订替换后，不再为其产生新的打卡提醒。消息通过用户、类型及事件编号唯一约束去重。

## 接口与升级

- `GET /api/notifications?page=1&pageSize=20&unreadOnly=false` 返回本人分页消息。
- `GET /api/notifications/unread-count` 返回 `{count}`。
- `PUT /api/notifications/{id}/read`、`PUT /api/notifications/read-all` 标记本人消息已读，返回 204。
- `GET /api/notifications/settings` 读取设置，`PUT /api/notifications/settings` 保存全部设置。字段为 `checkinEnabled`、`checkinFrequency`（`PER_TASK` 或 `DAILY`）、`dailyTime`、`planDueEnabled`、`reportEnabled`、`dataChangeEnabled`、`importFailedEnabled`。

已有库执行 `database/migrations/012_notifications.sql`，新库使用最新 `database/schema.sql`。使用 JWT 和逻辑关联，无新增外键。此版本使用站内消息。

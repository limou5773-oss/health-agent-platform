# 计划修订与版本比较

对应 AG07。在「我的计划」打开计划并选择「修订计划」。可调整标题、依据、周期、任务日期、时间、内容和可选提醒时间。提交时填写调整说明。保存后比较当前计划和草稿，再确认应用或放弃草稿。确认后的版本可在同一页查看。

已有计划初始为第 1 版，第一次保存修订时记录初始计划快照。保存每份草稿时版本递增，放弃的版本号不复用。确认只接受最新草稿，同一最新版本已生效时重复确认返回当前计划。过期的保存、确认或放弃请求返回 409 `STALE_PLAN_REVISION`。

生效时保留未改动任务的编号。已打卡任务须保留原内容和日期，确认时重新检查。替换或移除的未打卡任务退出当前计划和新报告的应完成统计，原行保留以支持历史运动关联。已有报告保留生成时的快照。修订确认、任务变更和版本快照在同一事务内完成。

## 接口和升级

接口使用现有 JWT 鉴权，版本和任务按所属计划校验。

- `GET /api/plans/{id}/revisions` 返回 `activeVersion`、`latestVersion`、`draft` 和已生效的 `history`。
- `POST /api/plans/{id}/revisions` 接收 `expectedVersion`、`note` 和 `content`。内容包含 `title`、`reason`、`startDate`、`endDate`、`items`，每项包含可空的原任务 `id`、`date`、`time`、`title`、`description` 和可空的 `reminderTime`。周期为 1 至 31 天，包含 1 至 93 项任务。
- `POST /api/plans/{id}/revisions/{version}/confirm` 确认该草稿并返回计划。
- `DELETE /api/plans/{id}/revisions/{version}` 放弃该草稿并返回 204。

已有库执行 `database/migrations/011_plan_revisions.sql`。使用计划编号和任务编号逻辑关联。当前入口为计划编辑页面，提醒时间随版本保存，站内提醒调度由后续模块实现。

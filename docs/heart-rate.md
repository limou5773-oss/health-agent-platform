# 心率采样

在「心率采样」录入、更正、删除及查询实际测量。时间按个人档案时区填写，精度为秒，统一使用次/分。更正保留来源并显示更正标记。同一用户、来源和测量时刻只保留一条记录。

## CSV 导入

页面提供 UTF-8 模板。列顺序为 `measuredAt,bpm,sourceName,note`，测量时间示例为 `2026-01-01T08:00:00`，备注可留空。小米、红米、苹果或其他设备的数据都可整理成同一模板。

预览显示测量范围、来源、新增和重复条数。确认使用同一批已校验记录，事务内检查当前记录并写入。页面可查看最近 50 个批次和恢复未确认的预览。规则见[体重与心率 CSV 导入](measurement-imports.md)。设备原始心率文件解析尚未接入。

## 统计与时间线

看板显示周期及逐日采样均值、采样总数、有效天数和最新测量时间。周期均值以采样条数为分母。无记录的日期显示缺失。同一时刻出现多个来源时，保留原始记录，统计前需选择来源。时间线可筛选、查看、导出和按范围删除心率采样。

## 接口与数据库

接口位于 `/api/heart-rate`，沿用 JWT。已有库需执行 `database/migrations/022_heart_rate_samples.sql` 和 `database/migrations/023_measurement_imports.sql`，新库使用 `database/schema.sql`。表通过 `user_id` 逻辑关联。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/records` | 按开始日期、结束日期和可选来源分页查询 |
| POST | `/records` | 保存 `measuredAt`、`bpm`、`sourceName`、`note` |
| PUT / DELETE | `/records/{id}` | 更正或删除本人记录 |
| GET | `/sources` | 当前用户的来源列表 |
| GET | `/statistics` | 按开始日期、结束日期和可选来源统计 |
| POST | `/imports/preview` | 上传 multipart `file` 并生成批次 |
| GET | `/imports`、`/imports/{id}` | 批次历史、指定批次预览 |
| POST | `/imports/{id}/confirm` | 按批次确认导入 |

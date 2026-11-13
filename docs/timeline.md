# 健康事件时间线

## 按范围删除

在时间线应用筛选后，选择睡眠、步数、运动、饮食、饮水、体重、心率或日记之一，点击「预览删除」。确认区显示已选类型、日期、来源、匹配条数及可能影响的关联内容。列表与预览共用原始记录范围，包含全部匹配页。计划和打卡事件不进入此流程。

`GET /api/timeline/events/deletion-preview` 返回 `count`、`impact` 和 `fingerprint`。确认时用相同筛选条件及 `fingerprint` 请求 `DELETE /api/timeline/events`。用户行锁内重读范围，若记录发生变化则返回 409 `DELETE_SCOPE_CHANGED`，需重新预览。成功返回 `{deleted}`。

删除沿用各指标的原有业务方法。涉及睡眠或已授权日记时清理关联分析、报告、任务中的原请求和会话内容及 Agent 检查点。删除步数、运动、饮食、饮水和体重会使覆盖相应日期的本地健康报告失效。删除心率采样后，看板根据剩余记录重新统计。正式计划及打卡继续保留。检查点服务返回失败时，数据库事务回滚，已清除的外部检查点无法随数据库回滚。

页面的「导出已筛选记录」下载 UTF-8 JSON。接口为 `GET /api/timeline/events/export`，使用同一组开始日期、结束日期、事件类型和来源筛选条件，身份来自 JWT。响应保存筛选条件和 `records` 数组，记录顺序与列表一致，每项附原始详情。导出使用已应用的筛选，不受列表页码限制；无匹配项时返回空数组。

对应 US12。时间线按日期展示本人现有健康记录、日记、打卡和计划变更。查询直接读取原模块的数据，事件详情使用原记录编号定位。

| 类型 | 日期归属 | 展示时间 | 来源 |
| --- | --- | --- | --- |
| SLEEP | 醒来归属日期 sleepDate | 睡眠结束时间 | 原睡眠来源 |
| STEPS | activityDate | 区间结束时间 | 原步数来源 |
| WORKOUT | activityDate | 开始时间 | 原运动来源 |
| MEAL / WATER | 食用或饮用日期 | 实际记录时间 | 原记录来源 |
| WEIGHT | 测量日期 | 实际测量时间 | 原测量来源 |
| HEART_RATE | 测量日期 | 实际测量时间 | 原采样来源 |
| DIARY | 日记实际日期 | 实际记录时间 | 原日记来源 |
| CHECKIN | 实际打卡日期 | checkedAt | 计划打卡 |
| PLAN_CREATED | 创建日期 | createdAt | 计划管理 |
| PLAN_STATUS | 变更日期 | changedAt | 计划管理 |

健康记录时间沿用原模块的档案时区约定。计划创建、打卡和状态变更沿用系统记录时间。打卡详情同时提供计划安排日期；撤销打卡后该打卡从时间线消失。计划创建来自现有记录，暂停、恢复、结束的历史从执行 008 迁移后开始记录。同状态提交按幂等处理。

## 接口

- GET /api/timeline/events 接受 startDate、endDate（必填，最多366天）、page、pageSize、type（可选，上表枚举）及 sourceName（可选，完整名称精确匹配）。返回 {items,total,page,pageSize}，items 按 eventDate DESC、occurredAt DESC、type ASC、id DESC 排列。
- GET /api/timeline/events/{type}/{id} 返回单条原始详情，限定当前用户。缺少记录时返回404。
- TimelineEvent={type,id,eventDate,occurredAt,sourceName,summary}。列表只含摘要；详情额外含 details 对象。sourceName 的展示与查询使用原文字。
- details 按事件提供下表字段。日期及时间为字符串，数值为JSON数值，可空字段保留null。

| 类型 | details 字段 |
| --- | --- |
| SLEEP | startTime, endTime, sleepDate, durationMinutes, sourceApp, deviceBrand, deviceType, deviceModel, note |
| STEPS | startTime, endTime, steps, mode, note |
| WORKOUT | exerciseType, startTime, endTime, durationMinutes, planItemId, note |
| MEAL | mealType, foodName, portionAmount, portionUnit, calorieMode, caloriesKcal, referenceAmount, referenceCaloriesKcal, nutritionSource, note |
| WATER | amountMl, note |
| WEIGHT | weightKg, note |
| HEART_RATE | bpm, note, corrected |
| DIARY | mood, stressLevel, bodyFeeling, events, tags, note, agentAllowed |
| CHECKIN | planId, planTitle, itemTitle, scheduledDate, checkedAt |
| PLAN_CREATED | planId, planTitle, startDate, endDate, createdAt |
| PLAN_STATUS | planId, planTitle, previousStatus, status, changedAt |

前端以日期分组、分页浏览，通过路由参数定位原始详情。计划相关详情可进入原计划页面。时间线展示记录发生时间和来源，由用户查看事件的先后与同期分布。

## 持久化与验收

新增 plan_status_changes，通过 plan_id 逻辑关联 plans。状态更新与事件插入使用同一业务事务及已有计划行锁。其他事件直接投影原表。用户身份沿用 JWT，Mapper XML 使用绑定参数和归属条件。时间线中的日记详情仅向记录所属用户开放。

旧库执行 database/migrations/008_health_timeline.sql，新库使用最新版 schema.sql。开发检查包括 Java 打包、Vue 类型与生产构建及 Mapper、迁移静态核对。真实数据库与功能由用户验收。

2026年11月13日完成 Java 打包、Vue 类型检查和生产构建。TimelineMapper、PlanMapper 的 Java 方法与 XML 一致，008 与新库表定义一致。

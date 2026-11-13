# 个人基线分析

对应需求 US16。侧栏「个人基线」支持睡眠时长、步数、运动时长、饮食热量、饮水量和体重，可为两期指定同一个数据来源。

## 比较口径

- 默认按档案时区取当天及此前 6 天作为近期，此前连续 28 天作为历史基线，分别要求至少 5 天和 20 天有效记录。
- 自选两个含首尾日期的周期，每期 1 至 366 天。历史周期结束日期早于近期开始日期，每期有效天数至少达到本期总天数的 70%，所需天数向上取整。
- 均值以有效日期为分母，保留最多三位小数。两期都达标时，差值为近期均值减历史均值。覆盖不足时仍返回各期可计算的均值、有效天数及缺失日期。没有有效日期时均值为空。
- 睡眠、步数、运动、饮水和体重直接复用已有的每日统计口径及精度。饮食以当日已记录的手动与估算热量之和为日值，当天无记录或存在热量未知条目时算缺失。
- 沿用各模块的来源冲突规则。收到 `SOURCE_SELECTION_REQUIRED` 时，选择同一来源重新比较。记录变动后重新查询即更新结果。使用现有 Mapper/XML 和表结构，无需迁移。

## 接口

`GET /api/baseline` 使用 JWT 获取当前用户。必传 `metric`，取值为 `SLEEP`、`STEPS`、`WORKOUT`、`MEAL`、`WATER`、`WEIGHT`。可选 `sourceName`，为空时按各模块的全部来源规则计算。自选周期须传 `recentStart`、`recentEnd`、`historicalStart`、`historicalEnd`，格式为 `YYYY-MM-DD`。

响应返回 `metric`、`sourceName`、`recent`、`historical` 和可空的 `difference`。每个周期包含 `startDate`、`endDate`、`periodDays`、`validDays`、`requiredDays`、`coverageRate`（百分数）、`sufficient`、可空的 `average` 和 `missingDates`。

# 饮食与饮水管理实施契约

对应需求US09、日常数据共同规则及开发方案6.4。实现三餐/加餐、食物与份量、饮水记录和每日汇总。沿用JWT、Controller → Service/impl → Mapper/XML和逻辑关联，不使用外键。当前不增加食物数据库、拍照识别、自动医疗建议、导入适配或Agent综合分析。

## 数据与业务口径

- 饮食记录：consumedAt（档案时区的ISO本地时间，秒精度）、mealType（BREAKFAST/LUNCH/DINNER/SNACK）、foodName（1至100字符）、portionAmount（可空，正数，最多9位整数和3位小数）、portionUnit（G/ML/SERVING）、calorieMode（UNKNOWN/MANUAL/ESTIMATED）、caloriesKcal（可空）、referenceAmount（可空）、referenceCaloriesKcal（可空）、nutritionSource（必传字符串，允许空串，最多500字符）、sourceName（1至100字符）和note（字符串，最多1000字符）。
- UNKNOWN：caloriesKcal、referenceAmount及referenceCaloriesKcal须为空；不补造热量。MANUAL：caloriesKcal须为非负数（最多10位整数和2位小数），参考字段为空；允许份量未知，因为该值由用户明确填写而非平台估算。ESTIMATED：请求caloriesKcal为空，portionAmount和referenceAmount须为正数，referenceCaloriesKcal为非负数（最多10位整数和2位小数），nutritionSource须有内容；referenceAmount与portionAmount使用相同portionUnit。
- 估算仅做用户提供依据的算术换算：caloriesKcal=portionAmount×referenceCaloriesKcal÷referenceAmount，四舍五入至2位小数。参考份量同样最多9位整数和3位小数。返回估算值及原始参考依据；没有平台营养数据库或模型提供的隐藏热量。修改估算份量会重新计算；手动热量由用户自行修改，不按份量自动缩放。
- 份量未知的饮食仍可保存，列表标明“份量未记录”。UNKNOWN热量返回null；明确录入的0 kcal保留为0。createdAt、updatedAt与实际consumedAt分别保存。食物类型、来源名称区分大小写和重音，数据库与程序采用一致的身份比较口径。
- 饮食记录唯一身份为用户、consumedAt、mealType、foodName和sourceName；同一食物、餐次及来源同秒记录更正时修改原记录。饮水唯一身份为用户、consumedAt、amountMl和sourceName。数据库唯一约束拒绝重复录入；保留用户范围索引，不引入重复请求缓存或自动重试。
- 饮水记录：consumedAt、amountMl（正数，最多9位整数和2位小数，固定毫升）、sourceName、note。只累加实际保存记录；不预设每日饮水目标。
- 同秒、同食物及餐次的不同来源饮食记录视为来源冲突；同秒同水量的不同来源饮水记录也视为来源冲突。未选来源的统计返回409 SOURCE_SELECTION_REQUIRED，用户选择sourceName后再计算；其他独立事件正常累加，不猜测相邻时间或不同描述是否同一事件。
- 每日归属consumedAt的本地日期。无记录日期指标为null；热量未知不计成零。已知部分热量分别展示手动及估算数量和合计，标明未知条数，不能将不完整热量标为全天摄入总量。饮水平均值以有记录日期为分母，覆盖率按有记录日期计算。dataCutoff为查询范围内最新实际记录时间。
- 修改与删除后前端刷新对应记录及统计。既有报告仅引用睡眠，不为饮食/饮水修改使无关睡眠报告失效；综合报告接入按后续阶段维护来源变更提示。

## API /api/nutrition

除以下规定外沿用api-contract.md的JWT、错误格式、分页及用户隔离。查询参数使用既有Requests.SleepQuery；统计使用Requests.StatisticsQuery，日期必填且最多366天。列表按consumedAt日期筛选，sourceName为可选来源。

- GET /meals → Page<MealRecord>；POST /meals、PUT /meals/{id} 请求上述饮食字段 → MealRecord；DELETE /meals/{id} → 204。
- GET /meals/sources → string[]。
- GET /meals/statistics → MealStatistics。
- GET /water → Page<WaterRecord>；POST /water、PUT /water/{id} 请求上述饮水字段 → WaterRecord；DELETE /water/{id} → 204。
- GET /water/sources → string[]。
- GET /water/statistics → WaterStatistics。

MealRecord={id,consumedAt,mealType,foodName,portionAmount,portionUnit,calorieMode,caloriesKcal,referenceAmount,referenceCaloriesKcal,nutritionSource,sourceName,note,createdAt,updatedAt}。
WaterRecord={id,consumedAt,amountMl,sourceName,note,createdAt,updatedAt}。
MealStatistics={startDate,endDate,sourceName,periodDays,validDays,coverageRate,recordCount,knownCaloriesCount,unknownCaloriesCount,manualCaloriesCount,estimatedCaloriesCount,manualCaloriesKcal,estimatedCaloriesKcal,knownCaloriesKcal,dataCutoff,daily:[{date,recordCount,knownCaloriesCount,unknownCaloriesCount,manualCaloriesCount,estimatedCaloriesCount,manualCaloriesKcal,estimatedCaloriesKcal,knownCaloriesKcal}]}。knownCaloriesCount等于manualCaloriesCount与estimatedCaloriesCount之和。
WaterStatistics={startDate,endDate,sourceName,periodDays,validDays,coverageRate,recordCount,totalMl,averageMl,dataCutoff,daily:[{date,amountMl,recordCount}]}。

某类热量没有对应记录时其合计为null；存在明确0值时合计为0。knownCaloriesKcal仅在至少一条记录热量已知时为数值。总览及每日均采用相同规则。水量总和无记录时为0，但averageMl=null，逐日缺失amountMl=null。所有Decimal字段均返回JSON数值或null。

## 实施清单与分工

1. 后端NutritionController、NutritionRequests、NutritionModels、NutritionService/impl、NutritionMapper/XML；新增meal_records与water_records及005_nutrition_management.sql，新库与迁移定义一致。
2. 前端新增“饮食与饮水”导航、分页筛选列表、录入编辑删除、可核对记录详情、热量模式与依据、独立饮食/饮水汇总及逐日表。估算条件不全时保留可修正输入，展示入口错误，不自动补值。
3. 核对接口与本次差异，执行Java打包和Vue类型检查/构建，更新README及实现状态，按既有授权同步GitHub。

新增校验只对应外部输入格式、字段长度、数值精度、热量模式必要依据、归属及日期范围。错误由既有统一异常入口处理，不新增通用try/catch、重试、降级或品牌抽象。功能及真实数据验收由用户执行，不编写或运行自动化功能、回归、浏览器测试，不连接真实数据库或模型。

新表的食物及来源身份使用utf8mb4_0900_bin进行精确比较，包含尾部空格差异，使数据库唯一身份与Java事件键一致。数据库需MySQL 8.0.17或更高版本；该NO PAD排序规则的版本依据见[MySQL官方8.0.17说明](https://dev.mysql.com/doc/relnotes/mysql/8.0/en/news-8-0-17.html)。没有增加运行时版本探测或兼容回退。

2026年11月10日开发检查：Java `mvn -B -ntp -DskipTests package`、Vue `npm run build`（vue-tsc和Vite）通过；NutritionMapper的17个Java/XML方法一致，新库与005迁移中的两张表定义一致，未声明外键。真实数据库和业务功能尚待用户验收。

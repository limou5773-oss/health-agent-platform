# 体重管理实施契约

对应需求US10、7.1体重口径与开发方案6.4。提供测量明细、趋势、用户设置目标及阶段起止对比。沿用JWT、Controller → Service/impl → Mapper/XML和业务编号逻辑关联，不使用外键，不增加BMI、饮食建议、自动目标、设备同步或Agent综合分析。

## 测量与统计

- 记录字段：measuredAt（档案时区ISO本地时间，秒精度）、weightKg（正数，最多4位整数、3位小数）、sourceName（必填，最多100字符）、note（必传字符串，允许空串，最多1000字符）。输入及返回只使用千克kg，不猜测或自动换算斤、磅。实际测量时间与createdAt、updatedAt分别保存。
- 同日多次测量均保留。唯一身份为用户、measuredAt、sourceName；同来源同秒更正体重须修改原记录。来源按精确字符比较，数据库使用现有MySQL 8.0.17+的utf8mb4_0900_bin。保留唯一约束、用户范围索引和写事务。
- 每日代表值固定为当日测量时间最晚的一条，排序键measuredAt、id，均降序；保留当天原始记录条数及完整代表记录。不同来源在同秒测量视为来源冲突，未选sourceName的统计返回409 SOURCE_SELECTION_REQUIRED，用户明确选择后计算；不同时间的测量可以参与统一每日代表值选择，不平均不同测量。
- 统计日期必填，最多366天。缺失日期representative=null；averageKg按有记录日期的代表值计算，四舍五入至3位小数。coverageRate按有记录日期计算。recordCount为原始测量条数，validDays为有代表值的天数，dataCutoff为范围内最新测量时间。
- 阶段前后对比固定使用所选范围第一个和最后一个有效日的代表值（first、last），changeKg=last.weightKg-first.weightKg，正数表示增加、负数表示减少；展示实际日期、来源和原始编号。仅一天时二者相同、变化为0；无记录时first、last及changeKg均为null。不补造周期边界的测量。
- GET单条记录为代表值及阶段端点提供原始明细入口。修改或删除后刷新列表、统计和目标进度。档案原有weightKg是用户单独填写的信息，不自动覆盖或充当测量记录；既有睡眠报告不受体重编辑影响，综合报告来源维护按后续阶段实施。

## 体重目标

- 每个用户可设置、修改、删除一份当前目标：startDate、targetDate、targetWeightKg（与weightKg相同精度和正数约束）、sourceName（可空或空串表示未限定，最多100字符）。周期为1至366天，可用于当前或历史记录；不填默认目标，不从档案或模型推断基线。
- 目标配置与进度接口分开；即使进度有来源冲突，仍可查看、修改或删除目标配置。sourceName由用户设置，进度只使用目标周期及该来源范围的记录。
- baseline为周期内第一个有效日代表值，current为最后一个有效日代表值。目标进度随真实记录修改/删除实时重算，不保存历史目标版本。无记录时baseline、current、changeKg、differenceToTargetKg、progressRate、direction、goalReached及dataCutoff为null。
- 有记录时changeKg=current-baseline，differenceToTargetKg=current-target（正数表示高于目标，负数表示低于目标）。direction由target与baseline比较确定：LOSE、GAIN、MAINTAIN。
- 非维持目标progressRate=(current-baseline)/(target-baseline)×100，四舍五入至2位小数，允许负数及超过100%，不将原始比例静默截断；LOSE在current<=target时goalReached=true，GAIN在current>=target时为true。MAINTAIN不计算百分比（progressRate=null），只显示差值，goalReached按current==target判断，不引入容差或医学判断。
- 目标周期内的来源冲突同样返回409，由用户编辑目标来源后重算，不自动选来源或回退到其他时间范围。

## CSV 导入

「体重管理」提供千克模板、预览、批次确认和历史。入库后页面刷新测量记录、统计和目标进度。已有库需执行 `database/migrations/023_measurement_imports.sql`。详见[体重与心率 CSV 导入](measurement-imports.md)。

## API /api/weight

列表查询沿用Requests.SleepQuery的page、pageSize、startDate、endDate、sourceName。统计沿用Requests.StatisticsQuery的必填日期及366天上限；JWT、分页{items,total,page,pageSize}和错误{code,message}沿用api-contract.md。

- GET /records → Page<WeightRecord>；GET /records/{id} → WeightRecord，仅本人记录。
- POST /records、PUT /records/{id}：{measuredAt,weightKg,sourceName,note} → WeightRecord，新增返回201、修改返回200；DELETE /records/{id} → 204。
- GET /sources → string[]。
- GET /statistics → WeightStatistics。
- GET /goal → 200 WeightGoal，未设置目标时204无响应体；PUT /goal：{startDate,targetDate,targetWeightKg,sourceName} → 200 WeightGoal；DELETE /goal → 204（无目标时也为204）。
- GET /goal/progress → 200 WeightGoalProgress，未设置目标时204无响应体。复用前端api对204的处理，不返回空200供JSON解析，不以空对象代替未设置状态。

WeightRecord={id,measuredAt,weightKg,sourceName,note,createdAt,updatedAt}。
WeightStatistics={startDate,endDate,sourceName,periodDays,validDays,coverageRate,recordCount,averageKg,first,last,changeKg,dataCutoff,daily:[{date,recordCount,representative:WeightRecord|null}]}。first、last为WeightRecord或null。
WeightGoal={startDate,targetDate,targetWeightKg,sourceName,createdAt,updatedAt}。
WeightGoalProgress={goal:WeightGoal,periodDays,validDays,coverageRate,baseline:WeightRecord|null,current:WeightRecord|null,changeKg,differenceToTargetKg,progressRate,direction,goalReached,dataCutoff}。

Decimal均返回JSON数值或null；coverageRate为数值，goalReached为boolean或null，direction为LOSE/GAIN/MAINTAIN或null。重量、差值、均值显示最多3位小数，覆盖率与百分比最多2位。

## 实施顺序与分工

1. 后端WeightController、WeightRequests、WeightModels、WeightService/impl、WeightMapper/XML，新增weight_records与weight_goals，schema与006_weight_management.sql定义一致。
2. 前端“体重管理”导航、测量增删改查/筛选/分页/详情、含原始代表记录的逐日趋势与阶段对比、目标配置与独立进度。冲突只展示后端判断结果，不用旧错误阻止用户修改查询；记录变化后刷新三处数据。
3. 静态核对接口与diff，Java打包、Vue类型检查及构建，更新README/实现状态并按既有授权同步GitHub。

输入只在DTO入口校验格式、精度、字段长度及日期范围，内部依赖契约；保留归属、事务和唯一约束，不增加重复判空、通用try/catch、重试或兼容抽象。功能测试和真实数据验收由用户负责，不写/运行自动化功能、回归或浏览器测试，不连接真实数据库或模型。

2026年11月10日开发检查：Java `mvn -B -ntp -DskipTests package`、Vue `npm run build`（vue-tsc和Vite）通过；WeightMapper的12个Java/XML方法一致，新库与006迁移的两张表定义一致，未声明外键。以上仅为编译构建及静态检查，真实数据库和功能尚待用户验收。

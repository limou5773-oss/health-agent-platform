# 运动管理实施契约

对应需求US08及开发方案6.4。本次交付步数、运动记录、每日步数目标、CSV预览确认和现有计划任务关联。沿用JWT、Java三层、MyBatis XML和业务编号逻辑关联，不引入外键。Agent的综合多指标分析按后续开发阶段实施，本次不宣称已经支持。

## 统计与业务约定

- 步数记录包含startTime、endTime、steps（非负整数）、mode（CUMULATIVE或DELTA）、sourceName及note。日期为开始日期；区间至少1秒，须在同一本地日期内，可恰好结束于次日零点。CUMULATIVE必须从当日零点开始。
- 每个用户、来源、日期只使用一种步数语义；混合累计与增量时返回409。累计快照取结束时间最新的一条，不将快照相加；增量区间不得重叠，首尾相接允许。更正快照通过修改原记录完成。
- 同一用户、来源、起止时间构成步数唯一记录；重复且步数及mode相同的CSV行跳过，内容不同则提示冲突。数据库唯一约束和用户行锁保护写入。
- 统计时同一天出现多个步数来源且未指定sourceName，返回409 SOURCE_SELECTION_REQUIRED；每日保留实际采用的来源。没有记录的日期steps=null，真实零步为0；平均值按有记录日期计算。
- 每日步数目标由用户设置为正整数；未设置时dailyGoal、goalDays及goalRate=null，不生成默认健康目标。目标比例可超过100%，达标天数按实际记录及当前设置的目标计算，不保存历史目标版本。
- 运动记录包含exerciseType（自由文本，最多50字符）、startTime、endTime、sourceName、note及可选planItemId，时长由程序以秒计算为分钟，按开始日期归档。相同用户、起止时间、类型及来源不重复录入。
- 运动列表按开始日期归档；运动统计读取与所选日期窗口实际相交的记录，裁剪至窗口并在本地零点分割后计算每天的区间并集，避免跨日或重叠运动重复计时。每日recordCount为当天相交记录数，周期recordCount为窗口相交记录的不同编号数量。不同来源实际重叠且未选来源时返回409。运动与步数的来源筛选独立。
- 新增或变更运动的任务关联时，须选择本人、同开始日期、进行中计划的一项任务；列表显示关联任务及真实打卡状态。计划后来暂停或结束时，修改其他运动字段可保留同日原关联，不要求解除历史关系。保存或删除运动不自动改变打卡；用户单独使用既有打卡接口确认，暂停计划继续遵守既有打卡规则。

## API /api/activity

查询参数使用现有SleepQuery结构：page、pageSize、startDate、endDate、sourceName；统计要求起止日期必填且不超过366天。返回分页仍为{items,total,page,pageSize}，错误仍为{code,message}。

- GET /steps → Page<StepRecord>。
- POST /steps、PUT /steps/{id}：{startTime,endTime,steps,mode,sourceName,note} → StepRecord；DELETE /steps/{id} → 204。
- GET /steps/sources → string[]。
- GET /steps/statistics → {startDate,endDate,sourceName,periodDays,validDays,coverageRate,totalSteps,averageSteps,dailyGoal,goalDays,daily:[{date,steps,sourceName,goalRate}]}。
- GET /goal → {dailySteps:number|null}；PUT /goal {dailySteps:正整数} → 相同结构。
- POST /step-imports/preview：multipart file、timezone（可选，默认档案时区）；UTF-8、最多10MB及10000行，表头startTime,endTime,steps,mode,sourceName,note。时间为ISO本地时间或带offset，转换为档案时区后按上述日期/语义规则校验。每行保留错误，不直接写正式记录。
- POST /step-imports/{id}/confirm → StepImportBatch；GET /step-imports → StepImportBatch[]。确认在事务内重新核对当前业务状态，同批确认幂等；预览后发生新的语义冲突时明确返回409，不吞错。成功确认后清理临时预览行，历史仅保留批次计数。
- StepImportBatch={id,status,totalRows,validRows,invalidRows,duplicateRows,insertedRows,createdAt,rows?}；rows为预览行数组：{rowNumber,startTime,endTime,steps,mode,sourceName,note,valid,error}。其中steps为原始文本，避免无效数字无法预览。重复数是有效数的子集。
- GET /workouts → Page<Workout>；POST /workouts、PUT /workouts/{id}：{exerciseType,startTime,endTime,sourceName,note,planItemId:number|null} → Workout；DELETE /workouts/{id} → 204。
- GET /workouts/sources → string[]。
- GET /workouts/statistics → {startDate,endDate,sourceName,periodDays,validDays,coverageRate,totalMinutes,recordCount,daily:[{date,minutes:number|null,recordCount}]}。
- GET /plan-items?date=yyyy-MM-dd → [{id,planId,title,planTitle,date,completed}]，仅返回本人同日进行中计划的可关联项目。

StepRecord={id,startTime,endTime,activityDate,steps,mode,sourceName,note,createdAt}。
Workout={id,exerciseType,startTime,endTime,activityDate,durationMinutes,sourceName,note,planItemId,planId,planItemTitle,planItemCompleted,createdAt}。未关联时planItemCompleted及关联字段为null。

## 实施顺序与验收分工

1. 后端、Mapper XML、新库表及004迁移：步数语义、统计、目标、导入事务、运动与计划归属。
2. 前端运动管理页：步数/运动双区、筛选分页、统计、目标、录入编辑删除、CSV模板预览确认、计划关联及显式打卡。
3. 核对接口与差异，运行Java打包、Vue类型检查和构建，更新README及实现状态并同步GitHub。

不新增自动化功能或回归测试，不连接真实数据库、模型或设备。功能和真实数据验收由用户执行。Mi Fitness专用原始文件仍需真实脱敏样本适配，活动CSV同样是明确的通用模板，不宣称直接解析其私有格式。

新增校验仅对应步数整数与MySQL INT范围、累计/增量区间约定、字段长度、日期窗口和计划归属/状态。数据库保留索引、唯一约束、业务CHECK和事务；活动来源及运动类型按区分大小写与重音的规则保存，保持SQL来源身份与程序比较口径一致。没有新增重试、静默降级或通用异常包装。

2026年11月10日开发检查：Java `mvn -B -ntp -DskipTests package` 通过；前端 `npm run build`（vue-tsc与Vite）通过；ActivityMapper的29个映射方法与XML一致，新库与004迁移的五张活动表定义一致，差异空白检查通过。这些结果不代表真实数据库、设备文件或业务功能已验收。

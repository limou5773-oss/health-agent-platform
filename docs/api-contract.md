# 第一阶段接口契约

实现范围：睡眠健康管理主流程、运动管理、饮食饮水、体重记录与目标、情绪与日记及可选的日记背景读取。专用品牌格式、提醒及其余P1/P2不伪装为已实现。Java 21 / Spring Boot 3.5 / MyBatis XML / MySQL；Vue 3；FastAPI + LangGraph。所有日期使用 ISO yyyy-MM-dd；时间使用 ISO 本地日期时间并按用户档案时区解释。接口返回原始 JSON，不另套多层 success/data。分页返回 {items,total,page,pageSize}。统一错误 {code,message}。

## 约束

- Controller → Service / impl → Mapper + resources/mapper/*.xml。JWT签名、过期、账号状态在拦截器入口校验一次，绑定当前用户；请求DTO一次@Valid。内部不重复格式校验或判空，归属/版本/状态检查放负责的业务操作处。
- 不做通用自动重试、静默降级、兼容层、单次逻辑乱抽函数；不滥用try/catch。任务执行边界负责记失败，其余HTTP异常统一处理。不编写或运行功能/回归测试；只做编译构建。
- 不写入真实凭证。JWT_SECRET至少32字节，INTERNAL_SERVICE_KEY由环境提供。MySQL独立库health_agent。
- 表间通过业务编号逻辑关联，不建立数据库外键。关联删除由业务事务显式处理；删除任务时删除执行步骤、解除计划和报告的task_id关联，保留正式计划与报告。步骤写入锁定对应任务并检查当前状态，避免与任务删除并发产生孤儿记录。
- 用户数据按当前身份筛选；Python工具身份由Java任务归属确定，不接受模型userId。只有当前aiConsent=true的用户能向外部模型发起任务及继续工具查询。

## 用户接口 /api

除POST /auth/register、POST /auth/login和GET /health，其余均带Authorization: Bearer JWT。

- POST /auth/register {username,password,nickname} → 201 {id,username,nickname,role}。username 3..32，password 8..72字节（BCrypt限制），nickname 1..40；role USER由服务端决定。
- POST /auth/login {username,password} → {accessToken,expiresAt,user:{id,username,nickname,role}}。
- GET /auth/me → {id,username,nickname,role}。
- PUT /auth/password {oldPassword,newPassword} → 204。
- GET /profile 返回档案和授权字段。注册创建初始档案，timezone 为 Asia/Shanghai，总授权默认关闭，未知数值返回 null。
- PUT /profile 更新档案和授权字段。身高和体重允许 null。分类授权字段及撤回规则见 [AI 数据授权](ai-data-permissions.md)。
- GET /profile/export 下载当前登录用户的个人数据 JSON。字段与数据范围见 [个人数据导出](personal-data-export.md)。
- DELETE /profile/account 接收当前密码，删除当前登录的普通用户及关联数据，成功返回 204。详见 [删除账号和个人数据](account-deletion.md)。
- GET /sleep?page=1&pageSize=20&startDate=&endDate=&sourceName= → 分页SleepRecord。
- POST /sleep {startTime,endTime,sourceName,note} → SleepRecord。
- PUT /sleep/{id} 同POST → SleepRecord。
- DELETE /sleep/{id} → 204。删除使相关分析/报告失效，不能继续显示已删除健康内容。
- GET /sleep/statistics?startDate=必填&endDate=必填&sourceName=可选 → SleepStatistics。
- GET /sleep/sources → string[]（当前用户已录入的来源名称）。
- GET /sleep/export?startDate=&endDate= → UTF-8 CSV，本人范围，注意CSV公式注入转义。
- POST /imports/preview multipart file + format（CSV / APPLE_HEALTH，默认CSV）+ timezone（CSV使用，IANA，默认档案时区）+ sourceApp + deviceBrand + deviceType + deviceModel → ImportBatch及rows。CSV10MB、最多10000行；表头 startTime,endTime,sourceName,note，start/end ISO本地日期时间或带offset，转换至档案时区。保留问题行，预览不入sleep正式表。业务重复用唯一去重键；不静默吞掉坏行。
- POST /imports/{id}/confirm → {id,status,insertedRows,duplicateRows,invalidRows}。只入有效未重复行，同批确认幂等。所有来源保留；同一醒来日期内，不同来源时段实际重叠且未选择sourceName时，返回409 SOURCE_SELECTION_REQUIRED，由用户选择后统计。不同来源的非重叠时段可以汇总，不能因来源数量大于1就拒绝统计。
- 睡眠导入预览返回 `firstStartTime` 和 `lastEndTime`，即有效行最早的入睡与最晚的醒来时间，使用档案时区，包含有效重复行。无有效行时两个字段均为 null。此范围用于预览，确认和历史批次不保存。
- GET /imports → 当前用户导入批次概要数组。

### 可穿戴设备统一导入

- sourceApp：MI_FITNESS / APPLE_HEALTH / OTHER；deviceBrand：XIAOMI / REDMI / APPLE / OTHER；deviceType：BAND / WATCH / OTHER；这三项省略时为OTHER。deviceModel为选填文本，最多80字符，默认空串，入批次时去除首尾空白。参数只在Controller DTO入口校验一次。
- App、品牌、设备类型与文件格式独立。每批针对同一设备，用户提供的设备信息保存在ImportBatch，确认时复制到新增SleepRecord；不从文件名或型号猜品牌，不代表设备连接或官方认证。预览、确认返回及批次历史均含这四个字段。
- 小米、红米手环和手表共用CSV模板。Mi Fitness原始导出包尚未适配，不接受其CSV或ZIP冒充通用模板。支持范围及获取数据说明见wearable-import.md。
- sourceName仍为文件中实际来源名称，是去重与统计选择的标识；不同设备应使用不同sourceName。去重仍按用户、开始、结束和来源，不因设备说明字段不同而生成重复健康记录。已重复记录不会被本批设备说明覆盖。
- 手动新增的记录设备字段为OTHER及空型号；修改睡眠时间、来源或备注保留原设备信息。现有四列表头CSV导出保持不变，不包含设备说明字段；重新导入时重新选择设备。旧记录不推断硬件型号。

### 苹果健康格式

- 统一入口 /imports/preview 使用format=APPLE_HEALTH；原有POST /imports/apple/preview继续接受multipart file，内部直接调用同一入口，固定format与sourceApp为APPLE_HEALTH，设备品牌及类型为OTHER。二者均复用 /imports/{id}/confirm 确认入库。接受 export.xml 或包含 export.xml / apple_health_export/export.xml 的 ZIP；文件最多100MB，ZIP总展开内容最多500MB、最多10000个条目，最多10000条SleepAnalysis记录。时间采用文件内偏移并统一到档案时区，不另猜时区。
- 导入批次新增 format（CSV / APPLE_HEALTH）、skippedRows、unsupportedRows；CSV的后两项为0。Apple的totalRows为全部Record条数；有效、无效、跳过、不支持互斥计数，重复数属于有效数。预览rows仅保留睡眠条目及其问题项，不保存其他健康指标原文。
- 支持 HKCategoryTypeIdentifierSleepAnalysis 下的 Asleep / AsleepUnspecified、AsleepCore、AsleepDeep、AsleepREM。InBed、Awake不计入睡眠时长，其他指标跳过，未知睡眠值作为不支持项显示。跨日归属使用同来源连续或重叠区间的结束日期，清醒、在床区间仅用于识别连续记录边界，不计入时长，不猜测未记录的间隔。
- ImportRow新增sleepDate；Apple分段保留原阶段和原始偏移时间于note。CSV仍按结束日期归属。durationMinutes为数值，可包含小数；录入和CSV支持至少1秒的有效区间，统计按秒合并后换算小时，避免截断短睡眠阶段。
- 只读取压缩包中的数据，不将条目解压到文件系统；XML关闭外部实体和DTD解析。该边界依据需求NF03的文件结构、大小、记录数和资源限制，未引入通用重试或兼容框架。
- 经用户确认，导入入口按同来源、同睡眠日期检查真实睡眠覆盖范围的档案时区UTC偏移。偏移发生变化时，该组睡眠条目计入unsupportedRows并提示使用UTC档案时区重新导入，不进入正式记录；该判断只在苹果文件解析入口执行。
- 解析代码是否符合真实导出样本由用户验证；未验证前不记录为已接入设备或已通过样本验收。
- GET /knowledge?query= → Article[] {id,title,content,sourceName,sourceUrl,updatedAt,published}。只返回已发布资料，关键词检索。

SleepRecord={id,startTime,endTime,sleepDate,durationMinutes,sourceName,sourceApp,deviceBrand,deviceType,deviceModel,note,corrected,createdAt}。sleepDate按醒来日期，durationMinutes由程序计算。

睡眠、步数、体重和心率的更正接口保留已存记录的 `sourceName`，保存后设置 `corrected=true`。请求沿用原字段，新增时仍可填写来源。时间线详情及个人数据导出包含更正标记。升级已有数据库需执行 `032_record_corrections.sql`，旧步数和体重记录初始未标记更正。

SleepStatistics={startDate,endDate,sourceName,sources,periodDays,validDays,coverageRate,averageHours,totalHours,recordCount,dataCutoff,scheduleVariation,daily:[{date,hours,recordCount,firstStartTime,lastEndTime}]}。`sourceName` 是来源筛选条件，`sources` 是当前范围内实际用于统计的来源名称，去重后排序，无记录时为空数组。coverageRate百分数0..100；无有效日期均值null，缺失日的hours、firstStartTime和lastEndTime均为null。按日期合并区间并集，不重叠累加重复时段。日期范围最多366天（交互限制在入口校验一次），缺失日不补零。

作息时刻按醒来归属日期分组，展示各日最早入睡和最晚醒来。仅在同一查询范围及来源筛选下比较相邻、均有记录的日期。两天的本地钟表时间取跨午夜也成立的最短差，例如 23:50 与 00:10 相差 20 分钟。`scheduleVariation` 返回 `comparedDays`（日期对数）、`averageStartChangeMinutes` 和 `averageEndChangeMinutes`。无符合条件的日期对时为 null。早期已保存的任务或报告可能没有这些字段。

Agent 的睡眠统计工具沿用相同的逐日时刻和作息变化字段。新生成的分析及睡眠报告将本轮工具返回值保存在统计快照中。

## 运动管理

步数、运动、步数目标、CSV预览确认及计划任务关联使用独立的 `/api/activity` 接口，完整字段与口径见[运动管理实施契约](activity-management.md)。JWT、身份隔离、分页和统一错误格式沿用本契约。

累计步数取同来源当日最新快照，增量按不重叠区间累加；同日多个来源须明确选择。目标未设置时不推断目标。保存或删除运动记录不自动更改既有打卡状态。运动和睡眠目前分别统计，Agent以睡眠统计为主并可选授权日记背景；活动等指标的综合分析按后续阶段扩展。

## 饮食与饮水

`/api/nutrition` 提供饮食与饮水CRUD、来源、独立每日统计，完整请求、响应与计算约定见[饮食饮水实施契约](nutrition-management.md)。份量未知可保留记录；未记录、手动填写和有依据的估算明确区分。估算由Java按用户提供的份量、同单位参考值与出处计算，未提供依据时不生成热量。

汇总只显示已知部分热量，并提供未知记录数；缺失日期和未知热量返回null，实际0 kcal保留为0。饮水统计为所选范围的实际记录之和。同一事件多来源冲突时返回409 SOURCE_SELECTION_REQUIRED，由用户选择。当前报告以睡眠统计为主，可选授权日记背景；未增加饮食模型调用或营养数据服务。

## 体重管理

`/api/weight` 提供测量明细、单条详情、来源、每日代表值统计和目标配置/进度，完整字段与口径见[体重实施契约](weight-management.md)。单位固定为千克，保留同日多次记录，趋势取当天最后测量。阶段对比及目标起止值均使用可追溯的日代表记录；无数据不补零或推断基线，目标不设置默认值。

GET /weight/goal和/weight/goal/progress未设置目标时返回204，存在目标时这两个GET返回200结构化JSON。新增测量返回201，删除测量及目标返回204，其余成功查询或修改返回200。目标配置与进度独立，来源冲突不妨碍修改配置。现有档案体重独立保存，体重测量变更不改写档案或使无关睡眠报告失效；跨指标Agent与报告按后续阶段实施。

## 情绪与健康日记

`/api/diary` 提供本人日记增删改查、关键词/日期/来源/Agent权限筛选、逐条权限修改及原始详情，完整字段与授权规则见[日记实施契约](diary-management.md)。自述文字及主观压力不作诊断；agentAllowed默认为界面未勾选，API须明确传入boolean。

Agent须同时具备档案AI授权、任务includeDiary=true和日记agentAllowed=true才能读取任务周期内最近20条授权日记。内容变更或撤销使相关任务/草稿/检查点和标记包含日记的报告失效；正式计划保留，内部计划工具仅返回日期及状态等元数据，避免计划内衍生文字再次进入模型。

## 健康事件时间线

`GET /api/timeline/events` 按日期、类型及原始来源筛选本人事件，返回标准分页。单条详情通过 `GET /api/timeline/events/{type}/{id}` 读取。各类型的日期归属、原始详情和计划变更记录规则见[时间线接口](timeline.md)。

`GET /api/timeline/events/export` 按同一筛选范围导出本人记录及详情，格式为 JSON。

`GET /api/timeline/events/deletion-preview` 预览按单个指标、日期和来源删除的范围及影响。`DELETE /api/timeline/events` 使用相同筛选和预览返回的 `fingerprint` 确认删除。详见[时间线删除接口](timeline.md)。

## 个人基线

`GET /api/baseline` 比较同一用户、同一指标的近期与历史日均值，并返回两期覆盖和缺失日期。日期参数、来源和最低覆盖规则见[个人基线接口](baseline-analysis.md)。

## 综合健康报告

`POST /api/reports/health` 保存周报或月报，通过原报告列表和详情接口查询。请求、快照、版本及失效规则见[综合健康报告接口](comprehensive-health-reports.md)。

`POST /api/reports/focused` 保存专项报告或阶段对比。详见[专项报告接口](focused-health-reports.md)。

## 用户偏好记忆

`GET /api/profile/preferences` 查询当前用户的记忆，`PUT /api/profile/preferences/{kind}` 保存指定类别，`DELETE /api/profile/preferences/{kind}` 删除指定类别。Agent 通过已鉴权的运行上下文读取计划任务当前偏好。详见[偏好记忆接口](preference-memory.md)。

## Agent任务

- POST /agent/tasks {goal,kind,startDate,endDate,sourceName?,planStartDate?,planEndDate?,includeDiary?} → 202 TaskDetail；includeDiary默认为false，显式开启后只允许读取逐条授权的日记。kind ANALYZE / PLAN / REPORT。startDate/endDate为读取历史健康数据的分析窗口。PLAN另须传入planStartDate/planEndDate作为实际执行周期，最多31天；非PLAN省略这两项。P0不猜用户日期。
- COMBINED 任务创建及详情支持 `stepSource`、`workoutSource`、`weightSource`、`heartRateSource`。各字段可选，最长 100 字符。仅保存已授权指标的筛选值，追问沿用父任务的来源。
- 等待补充的非追问 COMBINED 任务可通过 `POST /agent/tasks/{id}/input` 更新这些筛选值。空值保留当前选择，用户和分析日期沿用原任务，恢复后重新执行统计。
- GET /agent/tasks → 当前用户TaskDetail概要数组（最多50条，按创建倒序）。
- GET /agent/tasks/{id} → TaskDetail。
- POST /agent/tasks/{id}/input {input,sourceName?} → 202 TaskDetail，待补充时继续同一任务；来源冲突时由用户明确选择sourceName，Java更新任务来源后恢复，模型不能擅自选择来源。
- PUT /agent/tasks/{id}/draft {version,title,reason,startDate,endDate,items} → TaskDetail，编辑只改草稿并递增版本，须WAITING_CONFIRMATION。
- POST /agent/tasks/{id}/confirm {version,operationId} → {planId,taskId,status:"COMPLETED"}。锁任务行，校验有效草稿版本，同一任务最多一份正式计划，重复确认返回同份计划，旧版本409。
- POST /agent/tasks/{id}/cancel → TaskDetail，取消后工具拒绝继续，已有正式结果不撤销。
- POST /agent/tasks/{id}/retry → 202 TaskDetail，只允许FAILED并由用户手动重试，不能重试权限/参数错误（标记retryable）；使用同一个任务/检查点；不新增自动重试。
- 同一模型轮次的工具按顺序分别提交检查点。后续工具失败时，手动重试沿用前面已提交的工具结果，从失败工具继续。工具失败摘要记录错误码、上游 HTTP 状态或异常类型。Java 不可用导致步骤无法写入时，失败原因由任务状态记录。
- DELETE /agent/tasks/{id} → 204，删除会话/任务及Python检查点，不泄露历史个人数据。

TaskDetail={id,kind,goal,status,startDate,endDate,planStartDate,planEndDate,sourceName,includeDiary,answer,question,error,retryable,statistics,draft,planId,reportId,steps,createdAt}。
status QUEUED/RUNNING/WAITING_INPUT/WAITING_CONFIRMATION/COMPLETED/FAILED/CANCELLED。
draft={version,title,reason,startDate,endDate,items:[{date,time,title,description}]}。草稿日期范围最多31天，items最多93条，均须在草稿周期内。
steps=[{id,name,status,inputSummary,outputSummary,createdAt}]，status RUNNING/COMPLETED/FAILED。摘要不保存密钥、完整健康文件或内部思维链。

## 计划与报告

- GET /plans → [{id,title,reason,startDate,endDate,status,createdAt}]。
- GET /plans/{id} → 同上 + {items:[{id,date,time,title,description,completed,checkedAt}]}。
- PATCH /plans/{id}/status {status:"ACTIVE"|"PAUSED"|"ENDED"} → Plan。
- POST /plan-items/{id}/checkins {date} → {id,completed:true,checkedAt}。任务date必须匹配，所属计划ACTIVE，同任务日期唯一，重复返回已有打卡。
- DELETE /plan-items/{id}/checkins → 204，当前用户撤销该项打卡。

计划详情包含打卡日历使用的任务数据、到期完成率和连续完成天数。详见[打卡日历口径](plan-checkins.md)。

计划详情提供独立的版本草稿、比较、确认和放弃接口。请求、版本并发及打卡保留规则见[计划修订接口](plan-revisions.md)。

周期复盘通过 `GET /api/plans/{id}/review` 读取完成情况及相邻阶段比较，通过 `PUT /api/plan-items/{id}/missed-reason` 保存未完成原因。详见[周期复盘接口](plan-review.md)。

主动任务通过 `GET/PUT /api/agent/triggers/settings` 管理开关、来源、分析窗口及消息设置，通过 `GET /api/agent/triggers/runs` 读取触发历史。详见[主动任务接口](agent-triggers.md)。

## 站内提醒与消息中心

`/api/notifications` 提供消息分页、未读计数、已读标记与提醒设置。各类别触发时机、去重和字段见[消息中心接口](notifications.md)。
- GET /reports → [{id,title,startDate,endDate,createdAt,sourceDeleted,includeDiary}]。
- GET /reports/{id} → 同上 + {content,statistics,references}，失效源不再返回旧健康明细。

## 后台

知识资料支持分类、排序、收藏筛选和按编号查看。详见[健康知识库接口](knowledge-library.md)。

全部 /admin 校验ADMIN角色。
- `GET /admin/runtime-stats` 按日期范围查询任务、工具、导入及计划汇总。详见[系统运行统计](runtime-stats.md)。
- `/admin/system-settings` 管理平台规则、模型名称和超时，并提供模型连接检查。详见[系统配置接口](system-settings.md)。
- `/admin/plan-templates` 管理计划模板，`/plan-templates` 提供用户选择及草稿生成。详见[计划模板接口](plan-templates.md)。
- GET /admin/imports、GET /admin/imports/{kind}/{id}/issues、PUT /admin/import-templates/{kind} 提供批次查询、问题分类和模板说明维护。详见[后台导入管理](import-admin.md)。
- GET /admin/users → [{id,username,nickname,role,enabled,createdAt}]。
- PATCH /admin/users/{id}/status {enabled} → 204，不允许停用自己。
- GET /admin/tasks → 任务编号、状态、类型、日期、耗时及通用失败提示。
- `GET /admin/tasks/{id}` 查询脱敏详情，`PUT /agent/tasks/{id}/feedback` 保存本人任务反馈。详见[后台任务管理](task-audit.md)。
- GET /admin/knowledge → 所有Article。
- POST /admin/knowledge {title,content,sourceName,sourceUrl,published} → Article。
- PUT /admin/knowledge/{id} 同上 → Article。

管理员通过可选环境ADMIN_USERNAME/ADMIN_PASSWORD初始化，密码必须用户配置；不种默认admin密码。知识初始材料可为“平台睡眠统计口径”，标记平台规则来源，不伪造医学引用。

## Java → Python

所有请求头 X-Service-Key: INTERNAL_SERVICE_KEY。
POST /internal/runs {taskId:string,taskToken:string,goal,kind,startDate,endDate,sourceName?,planStartDate?,planEndDate?,includeDiary}。PLAN草稿采用单独的计划执行周期，不把历史分析窗口当作执行日期。实际任务范围仍以Java授权context为准。
POST /internal/runs/{taskId}/resume {taskToken,action:"INPUT"|"CONFIRM"|"RETRY",input?,planId?}。
DELETE /internal/runs/{taskId} → 204，清除检查点。
GET /health → {status:"ok",modelConfigured:boolean}，无模型配置不假装分析可用。

调用返回 AgentResult={status,answer,question,statistics,draft,report}。status COMPLETED/WAITING_INPUT/WAITING_CONFIRMATION；无用字段null。report={title,content,references:[{id,title,sourceName,sourceUrl,updatedAt}]}。`updatedAt` 取自知识检索返回值，作为报告中的引用版本标识。Java分别持久化业务产物。Python出错用真实HTTP错误，Java任务执行边界记FAILED；模型错误不得返回预设健康报告。

Python使用OpenAI兼容外部模型接口，通过OPENAI_API_KEY/OPENAI_BASE_URL/OPENAI_MODEL配置；max_retries=0。Java负责账号JWT；Python只接收服务认证，不向浏览器公开工具。模型读取数据必须经真实工具调用，不能用硬编码结果替代。LangGraph使用本地SQLite检查点，服务单进程，等待补充/确认用interrupt与Command；恢复后不能重复写业务。确认后Java先事务落库并标记COMPLETED，再异步通知Python闭合图，闭合失败不改已保存计划的成功状态，记录明确错误摘要。

## Python → Java工具

头 X-Service-Key + X-Task-Token。任务编号作为路径，Java根据任务确定用户及当前授权，在统一内部入口校验一次。
- GET /internal/agent/tasks/{id}/context → {id,goal,kind,startDate,endDate,planStartDate,planEndDate,sourceName,includeDiary,status,aiConsent,timezone,planId}。
- GET /internal/agent/tasks/{id}/tools/profile 返回身高、时区、目标和偏好，体重仅在获授权时返回。
- GET /internal/agent/tasks/{id}/tools/sleep-statistics → SleepStatistics（直接使用任务保存的日期与来源，不允许模型扩大范围）。
- GET /internal/agent/tasks/{id}/tools/sleep-records → {totalCount,records:[{id,startTime,endTime,sleepDate,durationMinutes,sourceName,corrected}]}。仅已授权睡眠的分析、计划和睡眠报告任务可读。使用任务保存的用户、醒来日期范围和来源，按醒来时间倒序最多返回20条；不返回备注。完整周期统计仍由睡眠统计工具提供。
- GET /internal/agent/tasks/{id}/tools/combined-statistics → CombinedStatistics。仅 COMBINED 任务可用，按任务绑定的用户、日期及各指标来源查询，返回已授权指标的逐日数值和覆盖。
- GET /internal/agent/tasks/{id}/tools/knowledge?query= → Article[]。
- GET /internal/agent/tasks/{id}/tools/plans → 当前用户[{id,startDate,endDate,status}]，不返回标题、原因或正文；公共计划页面的接口不变。
- GET /internal/agent/tasks/{id}/tools/checkin-summary → {startDate,endDate,scheduled,completed,completionRate}。只查询任务绑定的用户和分析周期，按有效计划任务的安排日期核对打卡。无应完成任务时完成率为 null；工具不接收用户编号、日期或其他参数。
- GET /internal/agent/tasks/{id}/tools/diary → DiaryContext（结构见diary-management.md），仅任务显式includeDiary=true时可读，否则403 DIARY_NOT_ENABLED；只返回该用户、任务日期内当前已授权日记及其授权范围计数。
- POST /internal/agent/tasks/{id}/steps {name,status,inputSummary,outputSummary} → 201 {id}。

## 环境统一

Java SERVER_PORT=8080，DB_URL=jdbc:mysql://127.0.0.1:3306/health_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai，DB_USERNAME，DB_PASSWORD，JWT_SECRET，JWT_TTL_SECONDS=7200，INTERNAL_SERVICE_KEY，AGENT_URL=http://127.0.0.1:8001。
Python本地启动地址127.0.0.1:8001，JAVA_API_URL=http://127.0.0.1:8080，INTERNAL_SERVICE_KEY，OPENAI_API_KEY，OPENAI_BASE_URL，OPENAI_MODEL，CHECKPOINT_PATH=./data/checkpoints.sqlite。
Vue dev5173，通过Vite代理/api到8080；fetch统一带Bearer；JWT保存在sessionStorage，退出清除。正常无数据必须空状态；请求失败显示错误，不能静默伪装空列表。日期默认最近7天由客户端当天生成，示例CSV明确演示数据。

# 第一阶段接口契约

实现范围：可运行的睡眠健康管理主流程。其他指标、品牌解析、提醒及P2不伪装为已实现。Java 21 / Spring Boot 3.5 / MyBatis XML / MySQL；Vue 3；FastAPI + LangGraph。所有日期使用 ISO yyyy-MM-dd；时间使用 ISO 本地日期时间并按用户档案时区解释。接口返回原始 JSON，不另套多层 success/data。分页返回 {items,total,page,pageSize}。统一错误 {code,message}。

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
- GET /profile → {nickname,heightCm,weightKg,timezone,goal,preferences,aiConsent}，注册创建初始档案，timezone Asia/Shanghai，aiConsent false；未知数值返回null。
- PUT /profile {nickname,heightCm,weightKg,timezone,goal,preferences,aiConsent} → 同GET。可选身高/体重必须允许null，用户主动授权后方可分析。
- GET /sleep?page=1&pageSize=20&startDate=&endDate=&sourceName= → 分页SleepRecord。
- POST /sleep {startTime,endTime,sourceName,note} → SleepRecord。
- PUT /sleep/{id} 同POST → SleepRecord。
- DELETE /sleep/{id} → 204。删除使相关分析/报告失效，不能继续显示已删除健康内容。
- GET /sleep/statistics?startDate=必填&endDate=必填&sourceName=可选 → SleepStatistics。
- GET /sleep/sources → string[]（当前用户已录入的来源名称）。
- GET /sleep/export?startDate=&endDate= → UTF-8 CSV，本人范围，注意CSV公式注入转义。
- POST /imports/preview multipart file + timezone（IANA，默认档案时区）→ {id,status,totalRows,validRows,invalidRows,duplicateRows,rows:[{rowNumber,startTime,endTime,sourceName,note,valid,error}]}。CSV10MB、最多10000行；表头 startTime,endTime,sourceName,note，start/end ISO本地日期时间或带offset，转换至档案时区。保留问题行，预览不入sleep正式表。业务重复用唯一去重键；不静默吞掉坏行。
- POST /imports/{id}/confirm → {id,status,insertedRows,duplicateRows,invalidRows}。只入有效未重复行，同批确认幂等。所有来源保留；同一醒来日期内，不同来源时段实际重叠且未选择sourceName时，返回409 SOURCE_SELECTION_REQUIRED，由用户选择后统计。不同来源的非重叠时段可以汇总，不能因来源数量大于1就拒绝统计。
- GET /imports → 当前用户导入批次概要数组。

### P1 苹果健康文件导入

- POST /imports/apple/preview multipart file → 与CSV相同的导入预览结构，复用 /imports/{id}/confirm 确认入库。接受 export.xml 或包含 export.xml / apple_health_export/export.xml 的 ZIP；文件最多100MB，ZIP总展开内容最多500MB、最多10000个条目，最多10000条SleepAnalysis记录。时间采用文件内偏移并统一到档案时区，不另猜时区。
- 导入批次新增 format（CSV / APPLE_HEALTH）、skippedRows、unsupportedRows；CSV的后两项为0。Apple的totalRows为全部Record条数；有效、无效、跳过、不支持互斥计数，重复数属于有效数。预览rows仅保留睡眠条目及其问题项，不保存其他健康指标原文。
- 支持 HKCategoryTypeIdentifierSleepAnalysis 下的 Asleep / AsleepUnspecified、AsleepCore、AsleepDeep、AsleepREM。InBed、Awake不计入睡眠时长，其他指标跳过，未知睡眠值作为不支持项显示。跨日归属使用同来源连续或重叠区间的结束日期，清醒、在床区间仅用于识别连续记录边界，不计入时长，不猜测未记录的间隔。
- ImportRow新增sleepDate；Apple分段保留原阶段和原始偏移时间于note。CSV仍按结束日期归属。durationMinutes为数值，可包含小数；录入和CSV支持至少1秒的有效区间，统计按秒合并后换算小时，避免截断短睡眠阶段。
- 只读取压缩包中的数据，不将条目解压到文件系统；XML关闭外部实体和DTD解析。该边界依据需求NF03的文件结构、大小、记录数和资源限制，未引入通用重试或兼容框架。
- 经用户确认，导入入口按同来源、同睡眠日期检查真实睡眠覆盖范围的档案时区UTC偏移。偏移发生变化时，该组睡眠条目计入unsupportedRows并提示使用UTC档案时区重新导入，不进入正式记录；该判断只在苹果文件解析入口执行。
- 解析代码是否符合真实导出样本由用户验证；未验证前不记录为已接入设备或已通过样本验收。
- GET /knowledge?query= → Article[] {id,title,content,sourceName,sourceUrl,updatedAt,published}。只返回已发布资料，关键词检索。

SleepRecord={id,startTime,endTime,sleepDate,durationMinutes,sourceName,note,corrected,createdAt}。sleepDate按醒来日期，durationMinutes由程序计算。

SleepStatistics={startDate,endDate,sourceName,periodDays,validDays,coverageRate,averageHours,totalHours,recordCount,dataCutoff,daily:[{date,hours,recordCount}]}。coverageRate百分数0..100；无有效日期均值null，daily缺失日hours=null。按日期合并区间并集，不重叠累加重复时段。日期范围最多366天（交互限制在入口校验一次），缺失日不补零。

## Agent任务

- POST /agent/tasks {goal,kind,startDate,endDate,sourceName?,planStartDate?,planEndDate?} → 202 TaskDetail；kind ANALYZE / PLAN / REPORT。startDate/endDate为读取历史健康数据的分析窗口。PLAN另须传入planStartDate/planEndDate作为实际执行周期，最多31天；非PLAN省略这两项。P0不猜用户日期。
- GET /agent/tasks → 当前用户TaskDetail概要数组（最多50条，按创建倒序）。
- GET /agent/tasks/{id} → TaskDetail。
- POST /agent/tasks/{id}/input {input,sourceName?} → 202 TaskDetail，待补充时继续同一任务；来源冲突时由用户明确选择sourceName，Java更新任务来源后恢复，模型不能擅自选择来源。
- PUT /agent/tasks/{id}/draft {version,title,reason,startDate,endDate,items} → TaskDetail，编辑只改草稿并递增版本，须WAITING_CONFIRMATION。
- POST /agent/tasks/{id}/confirm {version,operationId} → {planId,taskId,status:"COMPLETED"}。锁任务行，校验有效草稿版本，同一任务最多一份正式计划，重复确认返回同份计划，旧版本409。
- POST /agent/tasks/{id}/cancel → TaskDetail，取消后工具拒绝继续，已有正式结果不撤销。
- POST /agent/tasks/{id}/retry → 202 TaskDetail，只允许FAILED并由用户手动重试，不能重试权限/参数错误（标记retryable）；使用同一个任务/检查点；不新增自动重试。
- DELETE /agent/tasks/{id} → 204，删除会话/任务及Python检查点，不泄露历史个人数据。

TaskDetail={id,kind,goal,status,startDate,endDate,planStartDate,planEndDate,sourceName,answer,question,error,retryable,statistics,draft,planId,reportId,steps,createdAt}。
status QUEUED/RUNNING/WAITING_INPUT/WAITING_CONFIRMATION/COMPLETED/FAILED/CANCELLED。
draft={version,title,reason,startDate,endDate,items:[{date,time,title,description}]}。草稿日期范围最多31天，items最多93条，均须在草稿周期内。
steps=[{id,name,status,inputSummary,outputSummary,createdAt}]，status RUNNING/COMPLETED/FAILED。摘要不保存密钥、完整健康文件或内部思维链。

## 计划与报告

- GET /plans → [{id,title,reason,startDate,endDate,status,createdAt}]。
- GET /plans/{id} → 同上 + {items:[{id,date,time,title,description,completed,checkedAt}]}。
- PATCH /plans/{id}/status {status:"ACTIVE"|"PAUSED"|"ENDED"} → Plan。
- POST /plan-items/{id}/checkins {date} → {id,completed:true,checkedAt}。任务date必须匹配，所属计划ACTIVE，同任务日期唯一，重复返回已有打卡。
- DELETE /plan-items/{id}/checkins → 204，当前用户撤销该项打卡。
- GET /reports → [{id,title,startDate,endDate,createdAt,sourceDeleted}]。
- GET /reports/{id} → 同上 + {content,statistics,references}，失效源不再返回旧健康明细。

## 后台

全部 /admin 校验ADMIN角色。
- GET /admin/users → [{id,username,nickname,role,enabled,createdAt}]。
- PATCH /admin/users/{id}/status {enabled} → 204，不允许停用自己。
- GET /admin/tasks → 任务编号、状态、类型、日期、耗时/失败摘要；默认不返回健康明细。
- GET /admin/knowledge → 所有Article。
- POST /admin/knowledge {title,content,sourceName,sourceUrl,published} → Article。
- PUT /admin/knowledge/{id} 同上 → Article。

管理员通过可选环境ADMIN_USERNAME/ADMIN_PASSWORD初始化，密码必须用户配置；不种默认admin密码。知识初始材料可为“平台睡眠统计口径”，标记平台规则来源，不伪造医学引用。

## Java → Python

所有请求头 X-Service-Key: INTERNAL_SERVICE_KEY。
POST /internal/runs {taskId:string,taskToken:string,goal,kind,startDate,endDate,sourceName?,planStartDate?,planEndDate?}。PLAN草稿采用单独的计划执行周期，不把历史分析窗口当作执行日期。
POST /internal/runs/{taskId}/resume {taskToken,action:"INPUT"|"CONFIRM"|"RETRY",input?,planId?}。
DELETE /internal/runs/{taskId} → 204，清除检查点。
GET /health → {status:"ok",modelConfigured:boolean}，无模型配置不假装分析可用。

调用返回 AgentResult={status,answer,question,statistics,draft,report}。status COMPLETED/WAITING_INPUT/WAITING_CONFIRMATION；无用字段null。report={title,content,references:[{id,title,sourceName,sourceUrl}]}。Java分别持久化业务产物。Python出错用真实HTTP错误，Java任务执行边界记FAILED；模型错误不得返回预设健康报告。

Python使用OpenAI兼容外部模型接口，通过OPENAI_API_KEY/OPENAI_BASE_URL/OPENAI_MODEL配置；max_retries=0。Java负责账号JWT；Python只接收服务认证，不向浏览器公开工具。模型读取数据必须经真实工具调用，不能用硬编码结果替代。LangGraph使用本地SQLite检查点，服务单进程，等待补充/确认用interrupt与Command；恢复后不能重复写业务。确认后Java先事务落库并标记COMPLETED，再异步通知Python闭合图，闭合失败不改已保存计划的成功状态，记录明确错误摘要。

## Python → Java工具

头 X-Service-Key + X-Task-Token。任务编号作为路径，Java根据任务确定用户及当前授权，在统一内部入口校验一次。
- GET /internal/agent/tasks/{id}/context → {id,goal,kind,startDate,endDate,planStartDate,planEndDate,sourceName,status,aiConsent,timezone,planId}。
- GET /internal/agent/tasks/{id}/tools/profile → Profile。
- GET /internal/agent/tasks/{id}/tools/sleep-statistics → SleepStatistics（直接使用任务保存的日期与来源，不允许模型扩大范围）。
- GET /internal/agent/tasks/{id}/tools/knowledge?query= → Article[]。
- GET /internal/agent/tasks/{id}/tools/plans → 当前用户计划概要。
- POST /internal/agent/tasks/{id}/steps {name,status,inputSummary,outputSummary} → 201 {id}。

## 环境统一

Java SERVER_PORT=8080，DB_URL=jdbc:mysql://127.0.0.1:3306/health_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai，DB_USERNAME，DB_PASSWORD，JWT_SECRET，JWT_TTL_SECONDS=7200，INTERNAL_SERVICE_KEY，AGENT_URL=http://127.0.0.1:8001。
Python本地启动地址127.0.0.1:8001，JAVA_API_URL=http://127.0.0.1:8080，INTERNAL_SERVICE_KEY，OPENAI_API_KEY，OPENAI_BASE_URL，OPENAI_MODEL，CHECKPOINT_PATH=./data/checkpoints.sqlite。
Vue dev5173，通过Vite代理/api到8080；fetch统一带Bearer；JWT保存在sessionStorage，退出清除。正常无数据必须空状态；请求失败显示错误，不能静默伪装空列表。日期默认最近7天由客户端当天生成，示例CSV明确演示数据。

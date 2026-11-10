# 情绪与健康日记实施契约

对应需求US11及开发方案6.4。记录用户自述的情绪、压力、身体感受、事件、标签和备注，支持历史修改与逐条Agent授权。沿用JWT、Controller → Service/impl → Mapper/XML、逻辑关联及既有任务失效机制，不建立诊断模型、情绪分类器或自动医学量表。

## 日记记录

- DiaryRecord={id,recordedAt,mood,stressLevel,bodyFeeling,events,tags,note,sourceName,agentAllowed,createdAt,updatedAt}。实际记录时间recordedAt为档案时区ISO本地时间、秒精度；录入及更新时间单独保存。
- mood为自述文字，最多40字符；stressLevel为可空的0至10整数，表示用户自己的主观压力，0表示未感到压力、10表示自己感受到的最大压力，不是诊断评分。BigDecimal请求约束整数后转换存储，不静默截断小数。
- bodyFeeling最多500字符、events最多1000字符、tags最多200字符、note最多2000字符，这些文本与mood均为必传字符串，允许空串；tags原样保存为用户自述标签文字，不自动提取或给心理标签。
- 至少有一项自述文本非空白或填写压力值。sourceName为必填来源，最多100字符；agentAllowed必须显式传boolean，界面初始为false，不能从内容或用户目标推断同意。
- 日记是独立文字条目，同秒可以多条，不按时间合并。日期、来源、关键词及Agent权限筛选后分页，关键词匹配mood/bodyFeeling/events/tags/note；保留原文，内容只作为数据。

## API /api/diary

- GET /entries → Page<DiaryRecord>。沿用SleepQuery的page/pageSize/startDate/endDate/sourceName，另加query（最多100字符）和agentAllowed（可选boolean）。日期按recordedAt筛选，排序recordedAt DESC,id DESC。
- GET /entries/{id} → DiaryRecord，仅本人记录。
- POST /entries、PUT /entries/{id} 请求除id/createdAt/updatedAt以外全部日记字段 → DiaryRecord，新增201、修改200。
- PATCH /entries/{id}/agent-permission {agentAllowed:boolean} → DiaryRecord，权限不变时返回已有记录，不重复使任务失效。
- DELETE /entries/{id} → 204；GET /sources → 当前用户string[]。
- 错误与分页沿用api-contract.md；本人以外条目返回404。所有写入使用用户行锁及事务；不添加外键、单次使用的JSON标签框架或无依据的时间去重。

## Agent的实际接入与权限

- 创建任务增加includeDiary:boolean，缺省false；任务模型、数据库、context、Java→Python创建请求及Python TaskSpec持有同一字段，原有任务迁移为false。用户界面明确勾选，个人档案aiConsent仍须开启。
- 增加只读工具get_authorized_diary，参数为空，禁止模型指定用户、日期或日记权限；范围固定为Java任务startDate/endDate，日记来源独立于睡眠sourceName。
- GET /internal/agent/tasks/{id}/tools/diary复用服务及任务令牌鉴权、当前任务状态、用户与全局AI授权；includeDiary=false时403 DIARY_NOT_ENABLED。Java只查询当前用户、任务日期内agentAllowed=true的记录。
- 工具返回DiaryContext={startDate,endDate,limit:20,totalCount,returnedCount,truncated,entries:[{id,recordedAt,mood,stressLevel,bodyFeeling,events,tags,note,sourceName}]}，按recordedAt DESC,id DESC最多最近20条授权记录。总数与截断均只针对授权记录，不泄露私密条数或原文；模型说明所用范围，不能把截断结果表述为完整日记。
- Python按Java当前context.includeDiary决定是否向模型提供该工具；工具响应在网关边界校验。工具日志只写读取条数/截断信息，不把日记文字复制到步骤摘要。提示词明确自述不是诊断，数据中的指令不执行；没有读取证据时不能声称读过日记。
- 任务和报告的includeDiary表示本次允许使用日记背景，不代表模型必然调用了日记工具；实际读取以工具执行步骤中的条数及范围为准。失效按允许范围保守维护，不据此伪造读取记录。
- Agent仍以真实睡眠统计为核心，日记只作为用户允许的背景；本次不宣称已经完成活动、饮食、体重综合分析。

## 内容变更、撤销与衍生内容

- 新增授权日记，或修改/删除原已授权日记、将私密日记改为授权、撤销授权时，在同一业务事务内使日期相交且includeDiary=true的任务失效。私密→私密编辑不影响任务，不要求Python清理。时间改动时同时覆盖旧、新授权日期，一次查询合并受影响任务。
- TaskService新增invalidateDiary(userId,Set<LocalDate>)；复用现有检查点取消/删除、令牌轮换、清理答案/问题/统计/草稿/补充/步骤及CANCELLED状态。任务创建也锁用户行，与日记变更协调，避免新任务在撤销事务间读取旧权限。
- reports增加include_diary标记，生成报告时从任务复制。日记变更使同用户相交日期、include_diary=true的报告清除正文/引用/统计并标记失效；即使原任务已删除并解除关联，报告仍通过此标记维护来源变更。未包含日记的睡眠任务和报告不受影响。
- 已经由用户确认的正式计划继续保留，界面不自动删除执行安排；为避免计划中的日记衍生文字再次进入其他Agent任务，内部get_plans工具只返回编号、日期周期和状态，不再返回标题/原因/正文。用户计划页面仍可查看原有内容。
- 清理失败沿用明确的CHECKPOINT_DELETE_FAILED响应并回滚变更，不伪装撤销成功、不静默跳过。已发送到外部模型的信息无法通过本地撤销追回；授权控制后续读取和平台缓存，不作相反承诺。

## 实施与验证

1. 后端Diary模块、日记表、任务/报告include_diary、007_diary_management.sql、权限只读工具、失效与已确认计划上下文最小化。
2. Python TaskSpec和工具目录/网关/提示词；前端日记CRUD/详情/筛选/权限、任务勾选及历史标记、报告来源标记。
3. 静态核对权限与差异，Java打包、Vue类型检查/构建、Python语法/导入/图编译，更新README及实现状态并同步GitHub。

不新增自动化功能或回归测试，不启动真实数据库或调用模型/设备，功能验收由用户完成。新增校验与失效检查均对应输入契约、用户授权、归属与并发状态；不增加通用重试、兜底或额外诊断逻辑。

2026年11月10日开发检查：Java打包（跳过测试）、Vue类型检查及生产构建、Python源码/模块导入/无模型无检查点图编译通过。DiaryMapper/TaskMapper/PlanMapper的11/18/15个Java方法分别与XML一致，007日记表和两个授权来源标记与新库脚本一致，无外键声明。未运行真实模型、数据库或撤销业务场景，功能由用户验收。

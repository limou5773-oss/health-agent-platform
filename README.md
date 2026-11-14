# 基于 Agent 与多源健康数据的智能健康管理平台

Vue 3 + Java 21 / Spring Boot + MyBatis XML + MySQL + FastAPI / LangGraph。以睡眠数据为首个完整流程，将真实记录、Agent工具调用、用户确认、正式计划与打卡报告连接起来，按开发文档继续增加运动管理等指标。

## 目录

| 路径 | 内容 |
| --- | --- |
| frontend | Vue 前端，通过 /api 调用 Java |
| backend | Controller → Service / impl → Mapper + XML 三层业务后端 |
| agent-service | Python Agent 编排、外部模型调用与本地检查点 |
| database | MySQL 建表和平台规则资料 |
| docs | 接口契约与实现状态 |
| scripts | Windows 安装、配置和三端启动 |

## 本地运行

需要 Java 21、Maven 3.9、Node.js 22.12+、Python 3.11+、MySQL 8.0.17 或更高版本。推荐使用 PowerShell 7。

1. 在 MySQL 中建立本项目独立数据库并导入 `database/schema.sql`。脚本不应导入已有其他项目数据库。MySQL 连接账号由你配置，程序不会自行连接或修改本机其他数据库。
2. 若已有 `.env`，直接编辑；首次配置可在项目根目录执行 `./scripts/init-env.ps1`，再填写 `DB_USERNAME`、`DB_PASSWORD` 和真实模型配置。脚本只生成本地随机密钥，不打印密钥，不覆盖已有 `.env`。
3. 执行 `./scripts/install.ps1` 安装依赖、创建 Python 虚拟环境并构建 Java。可用 `-Python 'D:\IT\python13.1\python.exe'` 指定 Python。
4. 分别在三个终端中从项目根目录启动：

```powershell
./scripts/run.ps1 backend
./scripts/run.ps1 agent
./scripts/run.ps1 frontend
```

前端地址 http://127.0.0.1:5173，Java 端口 8080，Python 端口 8001。`run.ps1` 会将根目录 `.env` 加载到当前进程；直接从 IDE 启动时需要自行配置相同环境变量。

注册普通账号后，在“健康档案”中明确开启模型分析授权。未配置外部模型时，普通录入、导入、统计和计划查询仍可使用；Agent 会报告配置缺失，不生成模拟健康分析。管理员只在同时提供 `ADMIN_USERNAME` 和 `ADMIN_PASSWORD` 时初始化，没有默认管理员密码。

## 数据与鉴权

JWT 通过 `Authorization: Bearer <token>` 发送，签名、过期与账号状态在统一入口检查一次。前端保存于 `sessionStorage`，退出会清除；已签发令牌不提供额外的退出黑名单机制。业务按绑定用户身份隔离，模型不能指定其他用户。

在“睡眠记录 → 可穿戴设备数据导入”选择来源App、品牌、手环或手表及型号，再选择通用CSV或苹果健康XML/ZIP，先预览再确认入库。小米、红米的Mi Fitness数据当前使用通用CSV；原始Mi Fitness导出包尚未适配，不支持直接上传。每批对应同一设备，设备说明保存在批次和正式记录中；不同设备应使用不同sourceName，设备说明不改变去重规则。详见[统一导入说明](docs/wearable-import.md)。

CSV表头为 `startTime,endTime,sourceName,note`，可下载空白模板；CSV最多10MB、10000行。苹果健康文件最多100MB、10000条睡眠记录，ZIP展开内容最多500MB、10000个条目，读取 `export.xml` 或 `apple_health_export/export.xml`。只将未分期、核心、深睡、REM计入睡眠，清醒、在床及其他指标跳过并计数，未知睡眠类型展示为不支持。

来源重叠需要明确选择统计来源；分段睡眠按同来源连续区间的结束日期归属，统计采用实际睡眠区间并集，以秒计算后换算小时。缺失日期不补零。苹果健康的原始阶段和偏移时间保留在记录备注中，文件导入不等于设备自动同步。真实导出样本仍需由用户验证，当前不声称已通过样本验收。

若同来源、同睡眠日期跨越档案时区的夏令时或UTC偏移切换，预览会将相关睡眠条目标为不支持，并提示使用UTC档案时区重新导入，避免按本地时间计算出错误时长。

Java 保存业务数据与权威统计；Python 通过受鉴权的任务工具取数，使用真实模型工具调用。正式计划在用户确认有效草稿版本后，由 Java 事务保存；Python 不直接写业务表。

表间使用业务编号逻辑关联，不声明外键。关联写入和删除由业务层事务维护，保留唯一约束与关联查询索引。删除任务时会显式清理执行步骤，并解除计划及报告的任务关联，正式计划和报告继续保留。

若已经使用旧版建表脚本创建了数据库，执行 `database/migrations/001_remove_foreign_keys.sql` 移除原有外键。新建数据库只使用当前 `database/schema.sql`，无需运行该迁移。迁移脚本仅针对本项目旧版自动生成的约束名称，保留表中数据及原有索引，不会由应用自动执行。

苹果健康导入新增了批次格式、质量计数和分段归属字段，并将分钟时长改为小数。升级既有数据库时执行 `database/migrations/002_apple_health_import.sql`；新库直接导入最新 `database/schema.sql`。若旧库还保留外键，另先执行适用的001迁移。程序不会自动修改已有数据库。

设备统一导入在批次和睡眠记录中新增App、品牌、类型及型号字段。已有库在适用的前序迁移后执行 `database/migrations/003_wearable_import.sql`；新库仍只导入最新schema.sql。历史硬件信息不作推断。

## 运动管理

“运动管理”提供每日步数、运动记录、步数目标、通用步数CSV预览确认和计划任务关联。累计步数取当天同来源最新快照，增量步数只累计不重叠区间；同日多来源不直接相加，由用户选择。真实零步与缺失日期分别展示，未设置目标时不生成默认目标。

运动记录可关联本人同日进行中的计划项目；录入或删除记录不自动打卡，用户另行确认。接口与CSV表头见[运动管理说明](docs/activity-management.md)。Mi Fitness专用原始文件仍需脱敏样本适配。

已有库在适用的前序迁移后执行 `database/migrations/004_activity_management.sql`，新库只使用最新schema.sql。脚本由用户自行执行，应用不会自动修改数据库。

## 饮食与饮水

“饮食与饮水”管理三餐、加餐、食物份量、实际进食时间及饮水记录，提供独立的日期/来源筛选、分页和每日汇总。份量未知仍可保存；热量区分未记录、用户录入及按参考值估算。只有实际份量、同单位参考份量、参考热量和出处齐全时才计算估算热量，修改份量后重新计算；平台不自动查询营养数据库或生成热量。

饮食汇总展示已知部分热量、手动/估算合计与未知条数；缺失热量不计成零，也不显示为完整的全天摄入量。饮水按实际记录相加，不预设目标；同一事件存在不同来源时由用户明确选择统计来源。当前不含拍照识别、专用文件导入或跨指标Agent分析。字段和口径见[饮食饮水说明](docs/nutrition-management.md)。

已有库在适用的前序迁移后执行 `database/migrations/005_nutrition_management.sql`，新库只使用最新schema.sql；应用不会自动执行迁移。

## 体重管理

“体重管理”保存实际测量时间、千克数、来源及备注，同日多次测量均可查看。每日趋势固定取当天最后一条测量；平均值按有效日期的代表值计算，缺失日期不补零。逐日代表值和阶段起止对比都可查看对应的原始测量编号、时间与来源。同秒存在多来源时，由用户明确选择统计来源。

体重目标由用户填写周期、目标千克数及可选来源，基线和当前值取该周期内真实的首个、最后一个有效日代表值。没有记录就不生成基线和进度；维持目标只显示差值，增重或减重按实际变化计算比例。修改或删除测量后重新计算，档案中原有体重不会自动覆盖测量。详见[体重管理说明](docs/weight-management.md)。

已有库在适用的前序迁移后执行 `database/migrations/006_weight_management.sql`，新库只导入最新schema.sql；应用不会自动执行迁移。

## 情绪与健康日记

“情绪与日记”保存自述情绪、0至10的主观压力、身体感受、日常事件、标签及备注，支持历史修改、详情、日期/来源/关键词/权限筛选。日记默认不允许用于Agent，字段不作心理或疾病诊断。

如需作为睡眠分析背景，先逐条允许日记，再创建任务时勾选“包含已授权日记”，档案AI授权也须开启。模型按需读取任务周期内最近20条已授权记录，明确截断范围；私密内容和私密记录数量不会返回工具，步骤摘要只记读取条数。修改、删除或撤销授权后，相关任务缓存/草稿及包含日记的报告会失效；已确认正式计划保留，内部计划工具只提供周期和状态，用户计划页面仍保留正文。详见[日记授权说明](docs/diary-management.md)。

已有库在适用的前序迁移后执行 `database/migrations/007_diary_management.sql`，新库只使用最新schema.sql。已发送到外部模型的信息不能通过本地撤销追回；应用不会自动执行迁移。

## 健康时间线

“健康时间线”按日期汇集睡眠、步数、运动、饮食、饮水、体重、日记、打卡及计划变更。可以按日期、类型、来源筛选，并打开每条事件的原始详情。睡眠按醒来归属日期展示，打卡保留实际时间和计划安排日期。

已有库执行 `database/migrations/008_health_timeline.sql`。计划创建和现存打卡来自原记录，暂停、恢复及结束从升级后开始记录。字段与日期口径见[时间线说明](docs/timeline.md)。

## 个人基线

「个人基线」比较近期与本人历史的指标均值，支持七项指标、自选周期和来源筛选。页面显示两期覆盖、所需有效天数和缺失日期。使用现有记录，无需数据库迁移。详见[个人基线说明](docs/baseline-analysis.md)。

## 综合健康报告

在「健康报告」生成已结束周期的综合周报或月报，查看七项指标、数据来源、覆盖和计划完成情况。同周期重生成保留版本。已有库执行 `database/migrations/009_comprehensive_health_reports.sql`。详见[综合健康报告说明](docs/comprehensive-health-reports.md)。

报告中心也支持七项指标的专项报告和双周期对比。已有库执行 `database/migrations/021_focused_health_reports.sql`。详见[专项报告与阶段对比](docs/focused-health-reports.md)。

## 用户偏好记忆

在「个人档案」逐项管理长期目标、运动偏好、作息和提醒时段。授权后的计划任务读取最新保存内容。已有库执行 `database/migrations/010_user_preferences.sql`。详见[偏好记忆说明](docs/preference-memory.md)。

## 计划修订

在「我的计划」编辑修订草稿，对照当前版本后单独确认。历史版本可查看，已打卡任务保留。已有库执行 `database/migrations/011_plan_revisions.sql`。详见[计划修订说明](docs/plan-revisions.md)。

## 站内提醒与消息中心

在「消息中心」开启所需类别，设置打卡提醒频率，查看、跳转和标记消息。已有库执行 `database/migrations/012_notifications.sql`。详见[站内提醒说明](docs/notifications.md)。

## 周期复盘

在「我的计划」展开「周期复盘」，查询完成率、未打卡原因和相邻周期健康指标对比，并可把改期建议带入修订草稿。已有库执行 `database/migrations/013_plan_reviews.sql`。详见[周期复盘说明](docs/plan-review.md)。

## 主动任务

在「健康助手 → 主动任务设置与记录」开启每周睡眠复盘或睡眠导入后分析，查看触发来源和任务结果。已有库执行 `database/migrations/014_agent_triggers.sql`。详见[主动任务说明](docs/agent-triggers.md)。

## 后台导入管理

管理员可查看睡眠、步数、体重和心率导入批次、失败分类，并维护 CSV 模板填写说明。已有库在前序迁移后执行 `database/migrations/025_measurement_import_admin.sql`。详见[后台导入管理说明](docs/import-admin.md)。

## 计划模板

管理员维护睡眠、运动和饮水模板。用户可在健康助手选择启用模板并生成待确认草稿。已有库执行 `database/migrations/016_plan_templates.sql`。详见[计划模板说明](docs/plan-templates.md)。

## 提醒与系统配置

在「管理后台 → 系统配置」可维护平台提醒和自动任务规则、模型名称及超时，并检查模型连接。已有库执行 `database/migrations/017_system_settings.sql`。详见[系统配置说明](docs/system-settings.md)。

## 系统运行统计

在「管理后台 → 运行统计」查看任务完成率、耗时、工具失败、导入量及计划完成情况。已有库执行 `database/migrations/018_runtime_stats.sql`。详见[统计口径](docs/runtime-stats.md)。

在「管理后台 → 任务运行」可查看脱敏步骤、产物编号、确认版本和反馈。用户在已完成任务详情提交反馈。已有库执行 `database/migrations/019_task_audit.sql`。详见[后台任务管理](docs/task-audit.md)。

在「知识资料」按分类浏览、检索和收藏。已有库执行 `database/migrations/020_knowledge_library.sql`。详见[知识库说明](docs/knowledge-library.md)。

## 心率采样

在「心率采样」录入测量值。综合看板按实际采样显示均值、采样数与覆盖天数，时间线支持查看和导出。已有库执行 `database/migrations/022_heart_rate_samples.sql`。详见[心率采样](docs/heart-rate.md)。

体重和心率均支持通用 CSV 模板、批次预览、确认及历史。已有库执行 `database/migrations/023_measurement_imports.sql`。详见[体重与心率导入](docs/measurement-imports.md)。

## Agent 综合分析

在「健康助手」选择「综合分析」，使用同一日期范围的睡眠、步数、运动、体重与心率统计，并可选已授权日记。已有库执行 `database/migrations/024_combined_agent_analysis.sql`。详见[Agent 综合分析](docs/combined-agent-analysis.md)。

## AI 数据授权

在「个人档案」设置外部模型授权和五类数据权限。已有库执行 `database/migrations/026_ai_data_permissions.sql`。详见[AI 数据授权](docs/ai-data-permissions.md)。

## 开发约定

同一输入在入口校验一次；内部不重复判空或滥用 `try/catch`。归属、权限、状态、版本、事务与数据库唯一约束保留。功能测试与验收由用户负责，不附带自动化回归框架；开发侧只执行必要的构建检查。

完整需求与后续功能边界见同工作区“健康管理平台文档”，当前代码的实际支持情况见 `docs/implementation-status.md`，接口字段见 `docs/api-contract.md`。

## 技术依据

- [Spring Boot 3.5 运行环境](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- [MyBatis Spring Boot Starter 与版本兼容](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)
- [JJWT 签发与校验 API](https://github.com/jwtk/jjwt#quickstart)
- [LangGraph 暂停与恢复](https://docs.langchain.com/oss/python/langgraph/interrupts)
- [Vue 3 项目工具链](https://vuejs.org/guide/quick-start.html)

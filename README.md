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

需要 Java 21、Maven 3.9、Node.js 22.12+、Python 3.11+、MySQL 8。推荐使用 PowerShell 7。

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

运动记录可关联本人同日进行中的计划项目；录入或删除记录不自动打卡，用户另行确认。运动与睡眠分别统计，当前Agent和报告仍限于睡眠，综合多指标分析按后续阶段实施。接口与CSV表头见[运动管理说明](docs/activity-management.md)。Mi Fitness专用原始文件仍需脱敏样本适配，通用CSV不代表已接入其私有格式。

已有库在适用的前序迁移后执行 `database/migrations/004_activity_management.sql`，新库只使用最新schema.sql。脚本由用户自行执行，应用不会自动修改数据库。

## 开发约定

同一输入在入口校验一次；内部不重复判空或滥用 `try/catch`。归属、权限、状态、版本、事务与数据库唯一约束保留。功能测试与验收由用户负责，不附带自动化回归框架；开发侧只执行必要的构建检查。

完整需求与后续功能边界见同工作区“健康管理平台文档”，当前代码的实际支持情况见 `docs/implementation-status.md`，接口字段见 `docs/api-contract.md`。

## 技术依据

- [Spring Boot 3.5 运行环境](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- [MyBatis Spring Boot Starter 与版本兼容](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)
- [JJWT 签发与校验 API](https://github.com/jwtk/jjwt#quickstart)
- [LangGraph 暂停与恢复](https://docs.langchain.com/oss/python/langgraph/interrupts)
- [Vue 3 项目工具链](https://vuejs.org/guide/quick-start.html)

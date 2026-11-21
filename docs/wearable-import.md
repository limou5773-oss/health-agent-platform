# 可穿戴设备统一导入

按用户补充要求，将开发方案第6.4节的设备数据接入统一为品牌无关的睡眠导入流程。主要来源为小米运动健康（Mi Fitness），包括小米、红米手环和手表。设备描述与文件解析分开，后续指标扩展继续按开发文档推进。

## 当前支持范围

| 数据来源 | 当前可导入文件 | 当前限制 |
| --- | --- | --- |
| Mi Fitness 中的小米、红米手环与手表 | Mi Fitness 导出 ZIP、健康明细 CSV，或对应指标的通用 CSV | 明细解析覆盖睡眠分期、分钟步数和心率采样，真实设备样本待验收 |
| 苹果健康或写入苹果健康的设备 | export.xml 或规定目录的 ZIP | 支持在睡眠、步数、心率、体重页面分别导入，真实样本待用户验证 |
| 华为运动健康 | 解压后的专业睡眠明细 JSON | 支持分期区间，单批对应一个设备，真实设备样本待验收 |
| 其他来源 | 整理后的通用睡眠CSV | 不宣称支持其专用格式 |

上述是文件导入能力，不含设备自动同步、蓝牙连接或小米账号登录。设备描述由用户填写，不推断设备是否真实连接。

## 使用方式

1. 在“睡眠记录 → 可穿戴设备数据导入”选择数据来源App、品牌、手环或手表，并填写实际型号（选填）。每批对应一个设备。
2. 小米、红米用户可选择“Mi Fitness CSV / ZIP”，打开账号导出的 ZIP 或 `*_MiFitness_hlth_center_fitness_data.csv`。如果 ZIP 中的明细加密，在页面填写导出包的解压密码。其他格式可整理为通用睡眠 CSV。通用模板固定为 `startTime,endTime,sourceName,note`，UTF-8 编码，每批最多 10 MB、10000 行。时间使用 ISO 日期时间，可带 UTC 偏移；无偏移时按所选文件时区转换到档案时区。
3. sourceName使用能区分实际设备的名称；两台设备即使型号相同也应区分来源。只填写实际睡眠区间；有分期时按实际睡眠阶段分别填写，不把清醒、在床区间当作睡眠，不用整个在床跨度代替睡眠时长。
4. 核对预览中的App、设备信息、来源、有效记录时间范围及问题项。时间范围取档案时区下最早入睡与最晚醒来，包含有效重复行。修改本批设备说明后，再次上传生成新预览。
5. 确认后写入有效且未重复的记录。正式记录及导入历史显示相同设备说明；不同来源重叠时继续使用原来源选择规则。

苹果健康步数在“运动管理 → 导入步数”选择“苹果健康 XML / ZIP”，上传后查看有效、重复、无效及已跳过的其他指标数。步数以样本区间增量写入，格式和时间转换见[运动管理实施契约](activity-management.md)。

设备信息不改变去重含义：同一用户、开始时间、结束时间及来源相同即为重复。更改设备描述不会重写已有重复记录。四列CSV导出不带设备描述，重新导入时须重新选择设备；编辑已有睡眠记录会保留其设备说明。

## 获取Mi Fitness数据

小米官方提供账号数据下载流程：登录小米账号，在隐私设置的“管理你的数据”选择Mi Fitness并申请下载，按提示绑定邮箱。具体界面以账号页面为准。

睡眠明细解析按公开实现描述的格式编写。CSV 每行的 `Value` 为单行 JSON，读取 `sleep`、`watch_night_sleep` 和 `watch_daytime_sleep`。兼容普通 CSV 和整行另包一层引号的导出。展开 `items` 或 `segment_details` 内的 `items`，按 Unix 秒换算到档案时区。状态 2、3、4 分别导入深睡、浅睡和 REM，状态 5 为清醒。归属日期取所在睡眠段的醒来日期。仅包含汇总时长的记录及档案时区跨 UTC 偏移切换的记录会标为不支持。

ZIP 由浏览器本地读取，限 100 MB 和 10000 个条目。只提取唯一的健康明细 CSV，整包、密码和包内其他文件留在本机。包内明细多于一份时，页面提示单独选择 CSV。

单设备文件直接预览。文件含多个睡眠设备来源时，先按 `Sid` 选择本批设备，可对照导出包里的 `hlth_center_data_source.csv` 确认设备。每批只导入所选设备，可以使用同一份文件逐个选择和确认。来源使用 `Mi Fitness · Sid`，同一来源参与重复记录的去重。账号 ID 和其他指标原文不保存。单个明细 CSV 最多 10 MB、10000 个预览项。文件记录数、拆分后的睡眠区间数和跳过的行数分别统计，跳过项包括其他指标和其他设备的睡眠行。Mi Fitness 汇总表和自动同步仍待接入。

在「运动管理 → 导入步数」选 Mi Fitness CSV / ZIP，可从同一明细文件读取 `Key=steps`。每条 `Value.steps` 对应从 Unix 秒 `Value.time` 开始的一分钟增量，缺少 `Value.time` 时使用行上的 `Time`。将区间换算为档案时区后，沿用步数预览的范围校验、区间冲突检查和重复确认。零值占位、其他指标及其他设备行计入跳过。档案时区的偏移切换或歧义会提示改用 UTC。多个来源按 Sid 分批确认。[公开明细字段说明](https://github.com/heshi2019/Mi-Fitness_DataAnalyze/blob/main/MiFitness_hlth_center_fitness_data.md) · [公开区间映射实现](https://github.com/soma-intelligence/open-health-sync/blob/cad8a65af0461f328931dbe65e03c635f701268c/connectors/xiaomi/mapper.go#L274)

在「心率采样 → 导入心率记录」选 Mi Fitness CSV / ZIP，可读取 `heart_rate` 和 `single_heart_rate`。心率值取 `Value.bpm`，缺少时取 `Value.hr`。测量时间依次取 `Value.time`、`Value.date_time` 或行上的 `Time`，均为 Unix 秒。每批选一个 Sid，最多预览该来源 10000 条明细。ZIP 在浏览器解压，包上限 100 MB，提取的明细 CSV 上限 10 MB。其他设备、静息心率汇总、其他指标及零值占位计入跳过。错误和冲突提示对应 CSV 行，出现档案时区的夏令时歧义时提示改用 UTC。[公开明细字段说明](https://github.com/heshi2019/Mi-Fitness_DataAnalyze/blob/main/MiFitness_hlth_center_fitness_data.md#6-key-heart_rate心率数据) · [公开心率和时间映射](https://github.com/soma-intelligence/open-health-sync/blob/cad8a65af0461f328931dbe65e03c635f701268c/connectors/xiaomi/mapper.go#L132)

[小米官方Mi Fitness数据导出说明](https://www.mi.com/ph/support/article/KA-11566/)

[Open Health Sync 的公开导出字段说明](https://github.com/soma-intelligence/open-health-sync/blob/cad8a65af0461f328931dbe65e03c635f701268c/docs/connectors/xiaomi.md#7-mapping)

小米官方云同步需要先开通小米账号及运动健康服务，完成应用权限审批。账号本人授权后才能读取对应指标。当前交付为用户下载文件后预览确认。[小米服务端接入指南](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2333)

## 导入华为运动健康

通过华为隐私中心申请数据副本，解压后在睡眠导入页选择「华为运动健康 JSON」。上传含 `type=9`、`deviceCode` 和 `samplePoints` 的专业睡眠明细数组。单次限 10 MB、10000 条分期明细，一次选择一个设备。按 `startTime` 和 `endTime` 的 Unix 毫秒换算到档案时区，再使用连续区间的结束日期归属。深睡、浅睡、REM 和午睡计入时长，清醒仅参与日期归属，无法识别的分期在预览中标为不支持。跨 UTC 偏移切换时提示改用 UTC 档案时区。每日汇总和其他指标跳过。设备原始编号仅在解析时使用，统计来源保存由其生成的固定编号。

格式依据来自公开导出解析实现，实际华为账号导出文件待用户核对。[华为数据副本申请说明](https://consumer.huawei.com/cn/support/content/zh-cn16044986/) · [睡眠明细字段](https://github.com/suqinghuai/Huawei-Health-Sleep-Data-Extraction-Tool/blob/main/main.py) · [分期键](https://github.com/TomSkywalker/Huawei-Health-Raw-Export-Tools-and-Strava-export/blob/main/aggregate_huawei_health.py)

## 数据与实现

- 沿用Controller → Service / impl → Mapper + XML、JWT用户隔离、预览确认事务及原统计流程；不建立设备注册表或品牌适配工厂。
- `sourceApp / deviceBrand / deviceType / deviceModel`作为批次及记录的设备说明快照保存，不使用外键。确认时从已锁定的批次复制，避免读取用户后来修改的表单。
- 统一预览入口通过 `format` 选择通用 CSV、苹果健康或 Mi Fitness 解析。原苹果预览地址委托同一业务方法。Mi Fitness 校验明细表头、设备来源、分期时间和档案时区，问题项由预览展示。
- 新库使用最新schema.sql；已有库按适用顺序执行001、002及003_wearable_import.sql。003保留历史数据，不猜旧记录硬件，只标记原苹果批次的文件来源App。

## 验证分工

开发运行 Java 编译打包、Vue 类型检查及生产构建。真实 Mi Fitness 文件验收由用户执行，需核对小米和红米设备样本的分期、醒来日期、步数和心率，以及多设备选择与重复导入结果。

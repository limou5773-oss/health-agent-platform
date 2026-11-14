# 体重与心率 CSV 导入

在对应模块下载空白模板，填入 UTF-8 CSV。体重表头为 `measuredAt,weightKg,sourceName,note`，单位为千克。心率表头为 `measuredAt,bpm,sourceName,note`，单位为次/分。填写档案时区下的 ISO 本地时间，秒精度，来源可填写设备或应用名称，备注可为空。单文件上限为 10 MB 和 10,000 条记录。

上传时校验并保存批次，展示测量时间范围、来源和预计新增及重复条数。格式错误或同来源、同一秒存在不同数值时返回问题行。核对后可更正原记录或文件。同来源、同秒、同值的记录只写入一次。来自不同来源的测量均保留，统计时沿用各模块的来源选择规则。

确认按用户和指标读取批次，在同一事务内根据当前数据处理重复与冲突。已确认的批次再次提交时返回上次结果。成功后删除临时行，历史保留批次状态、时间范围、写入及重复数量。待确认批次可从最近 50 个批次中恢复。设备的原始导出文件需先整理成模板。

接口基路径为 `/api/weight/imports` 和 `/api/heart-rate/imports`，使用 JWT。旧版心率文件直传确认接口已改为按批次确认。

| 方法 | 相对路径 | 结果 |
| --- | --- | --- |
| POST | `/preview` | 上传 multipart `file`，返回 `{batch,sources}` |
| GET | `/{id}` | 返回本人的 `{batch,sources}`，来源来自待确认记录 |
| POST | `/{id}/confirm` | 确认指定批次，返回实际写入结果 |
| GET | 基路径 | 最近 50 个同指标批次 |

上传时可额外指定 `format=APPLE_HEALTH`，读取苹果健康 `export.xml` 或导出 ZIP。体重读取 BodyMass（kg、lb），心率读取 HeartRate（count/min），统一保存至三位小数。测量时刻取原始 startDate，转换到档案时区，并在备注保留原时刻和值。夏令时重叠时刻须改用 UTC 档案时区。预览显示跳过的其他指标条数。所选指标存在格式、单位或冲突错误时，整批提示问题位置。原始文件单次上限 100 MB，ZIP 展开总量上限 500 MB，所选指标上限 10000 条。

已有库在 023 之后执行 `database/migrations/030_apple_measurement_import.sql`。新库使用最新 schema。批次与预览行通过 `batch_id` 逻辑关联。

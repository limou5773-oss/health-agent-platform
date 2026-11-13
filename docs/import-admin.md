# 后台导入管理

对应 AD03。管理员在「管理后台 → 导入管理」查看睡眠和步数批次，可筛选有问题的批次，查看编号、用户编号、类型、时间、状态及行数统计。页面展示固定问题分类和数量。原始记录、上传文件及逐行错误原文由用户端原流程管理。

预览业务校验拒绝且没有生成批次时，另保存用户编号、数据类别、错误码和时间。接口用负编号区分这类记录和已有批次编号。框架在进入预览接口前拒绝的请求沿用原 HTTP 错误。确认批次时在清理预览行之前保存分类计数。升级前已清理预览行的批次只能查看已有计数。

CSV 模板表头由同一份后端定义提供给下载和导入校验。管理员可编辑填写说明；说明随模板元数据返回，在用户下载时展示。空白模板包含 UTF-8 BOM 和表头。

## 接口与升级

- `GET /api/admin/imports?kind=SLEEP&issuesOnly=false&page=1&pageSize=20` 返回分页批次和已记录的预览拒绝。
- `GET /api/admin/imports/{kind}/{id}/issues` 返回固定分类及数量。预览拒绝返回一次业务错误码。
- `GET /api/import-templates/{kind}` 返回表头及说明，登录用户可读。
- `PUT /api/admin/import-templates/{kind}` 更新 `{instructions}`，最多 1000 字符。

类型为 `SLEEP` 或 `STEPS`。管理接口沿用 JWT 管理员校验。已有库执行 `database/migrations/015_import_admin.sql`，新库使用最新 `database/schema.sql`。问题分类以导入类型和批次编号逻辑关联。

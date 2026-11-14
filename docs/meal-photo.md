# 饮食拍照记录

对应 EX01。在「饮食与饮水」新增饮食记录，展开「从餐食照片填写」。选择 JPEG 或 PNG，点击识别，核对候选、识别把握和粗估份量。点击「填入」后仍可编辑食物、时间、份量和来源。成功保存才会产生正式饮食记录，失败或识别不到食物时可手动录入。

照片识别不计算热量。热量默认未知，用户可按原规则手工填写，或提供参考份量、热量及出处后计算。

`POST /api/nutrition/meals/photo-preview` 接收 multipart 字段 `photo`，要求本人 JWT 和个人档案的 AI 总授权。最多 5 MB、2500 万像素。Java 按图像内容识别格式，缩小图片并重新编码后再发给模型。预览流程不写入业务数据库或 Agent 检查点。

配置的模型需要同时支持图片输入和结构化输出。模型不可用时页面显示失败，可继续手动录入。无需数据库迁移。[ChatOpenAI 图片输入文档](https://docs.langchain.com/oss/python/integrations/chat/openai#multimodal-inputs-images-pdfs-audio)

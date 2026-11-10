-- 仅用于由本项目旧版 database/schema.sql 创建的 MySQL 8 数据库。
-- 新版 schema.sql 已无外键，新建库不要执行本文件。
-- 只删除旧版约束；保留表、业务数据及已有的关联索引。
-- 若自行修改过约束名，先用 SHOW CREATE TABLE 核对并替换对应名称。
-- MySQL 官方语法：https://dev.mysql.com/doc/refman/8.0/en/create-table-foreign-keys.html
USE health_agent;

ALTER TABLE profiles DROP FOREIGN KEY profiles_ibfk_1;
ALTER TABLE sleep_records DROP FOREIGN KEY sleep_records_ibfk_1;
ALTER TABLE import_batches DROP FOREIGN KEY import_batches_ibfk_1;
ALTER TABLE import_rows DROP FOREIGN KEY import_rows_ibfk_1;
ALTER TABLE agent_tasks DROP FOREIGN KEY agent_tasks_ibfk_1;
ALTER TABLE agent_steps DROP FOREIGN KEY agent_steps_ibfk_1;
ALTER TABLE plans DROP FOREIGN KEY plans_ibfk_1, DROP FOREIGN KEY plans_ibfk_2;
ALTER TABLE plan_items DROP FOREIGN KEY plan_items_ibfk_1;
ALTER TABLE checkins DROP FOREIGN KEY checkins_ibfk_1;
ALTER TABLE reports DROP FOREIGN KEY reports_ibfk_1, DROP FOREIGN KEY reports_ibfk_2;

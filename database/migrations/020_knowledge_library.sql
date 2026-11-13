USE health_agent;

ALTER TABLE knowledge_articles ADD COLUMN category
 ENUM('SLEEP','EXERCISE','NUTRITION','WELLBEING','GENERAL') NOT NULL DEFAULT 'GENERAL';
UPDATE knowledge_articles SET category='SLEEP',updated_at=updated_at
 WHERE title='平台睡眠统计口径' AND source_name='平台规则';

CREATE TABLE knowledge_favorites (
 user_id BIGINT NOT NULL, article_id BIGINT NOT NULL,
 PRIMARY KEY(user_id,article_id)
);

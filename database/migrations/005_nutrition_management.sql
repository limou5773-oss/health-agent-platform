-- Run explicitly on an existing health_agent database after migrations 001-004.
-- Requires MySQL 8.0.17+ for utf8mb4_0900_bin identity comparisons.
-- Business IDs are logical associations; no database foreign keys or cascading actions.
USE health_agent;

-- Binary NO PAD identities match Java's exact food/source comparisons.
CREATE TABLE meal_records (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL,
 consumed_at DATETIME NOT NULL, meal_type ENUM('BREAKFAST','LUNCH','DINNER','SNACK') NOT NULL,
 food_name VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL,
 portion_amount DECIMAL(12,3), portion_unit ENUM('G','ML','SERVING') NOT NULL,
 calorie_mode ENUM('UNKNOWN','MANUAL','ESTIMATED') NOT NULL,
 calories_kcal DECIMAL(26,2), reference_amount DECIMAL(12,3), reference_calories_kcal DECIMAL(12,2),
 nutrition_source VARCHAR(500) NOT NULL,
 source_name VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL, note VARCHAR(1000) NOT NULL DEFAULT '',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_meal_identity(user_id,consumed_at,meal_type,food_name,source_name),
 KEY ix_meal_user_source_time(user_id,source_name,consumed_at),
 CHECK(portion_amount IS NULL OR portion_amount > 0),
 CHECK(reference_amount IS NULL OR reference_amount > 0),
 CHECK(calories_kcal IS NULL OR calories_kcal >= 0),
 CHECK(reference_calories_kcal IS NULL OR reference_calories_kcal >= 0),
 CHECK(
   (calorie_mode = 'UNKNOWN' AND calories_kcal IS NULL AND reference_amount IS NULL AND reference_calories_kcal IS NULL)
   OR (calorie_mode = 'MANUAL' AND calories_kcal IS NOT NULL AND calories_kcal <= 9999999999.99
       AND reference_amount IS NULL AND reference_calories_kcal IS NULL)
   OR (calorie_mode = 'ESTIMATED' AND calories_kcal IS NOT NULL AND portion_amount IS NOT NULL
       AND reference_amount IS NOT NULL AND reference_calories_kcal IS NOT NULL AND CHAR_LENGTH(TRIM(nutrition_source)) > 0)
 )
);
CREATE TABLE water_records (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL,
 consumed_at DATETIME NOT NULL, amount_ml DECIMAL(11,2) NOT NULL,
 source_name VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL, note VARCHAR(1000) NOT NULL DEFAULT '',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_water_identity(user_id,consumed_at,amount_ml,source_name),
 KEY ix_water_user_source_time(user_id,source_name,consumed_at),
 CHECK(amount_ml > 0)
);

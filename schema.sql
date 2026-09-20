-- Schema initialization for AI Calorie & Weight Tracker
-- Designed for PostgreSQL, reflection-free, executed directly or via docker-entrypoint-initdb.d

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    google_id VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    picture_url VARCHAR(1024),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS daily_targets (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_date DATE NOT NULL,
    calorie_target INT NOT NULL,
    PRIMARY KEY (user_id, target_date)
);

CREATE TABLE IF NOT EXISTS meals (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    logged_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    meal_date DATE NOT NULL,
    description TEXT NOT NULL,
    image_path VARCHAR(1024),
    total_calories INT NOT NULL,
    ai_explanation TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS meal_items (
    id UUID PRIMARY KEY,
    meal_id UUID NOT NULL REFERENCES meals(id) ON DELETE CASCADE,
    item_name VARCHAR(255) NOT NULL,
    estimated_calories INT NOT NULL
);

CREATE TABLE IF NOT EXISTS daily_weights (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    weigh_date DATE NOT NULL,
    weight NUMERIC(5,2) NOT NULL,
    unit VARCHAR(10) NOT NULL,
    PRIMARY KEY (user_id, weigh_date)
);

CREATE INDEX IF NOT EXISTS idx_meals_user_date ON meals(user_id, meal_date);
CREATE INDEX IF NOT EXISTS idx_weights_user_date ON daily_weights(user_id, weigh_date);

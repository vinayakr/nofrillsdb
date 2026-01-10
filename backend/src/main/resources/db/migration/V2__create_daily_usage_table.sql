CREATE TABLE daily_usage (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    usage_date DATE NOT NULL,
    total_bytes BIGINT NOT NULL,

    CONSTRAINT uk_daily_usage_user_date UNIQUE (user_id, usage_date)
);

CREATE INDEX idx_daily_usage_user_id ON daily_usage(user_id);
CREATE INDEX idx_daily_usage_date ON daily_usage(usage_date);
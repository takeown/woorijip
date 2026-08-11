CREATE TABLE spending_question_daily_usage (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    usage_date DATE NOT NULL,
    request_count INTEGER NOT NULL CHECK (request_count > 0),
    PRIMARY KEY (user_id, usage_date)
);

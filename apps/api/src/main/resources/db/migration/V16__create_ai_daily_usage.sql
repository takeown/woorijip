CREATE TABLE ai_daily_usage (
    household_id BIGINT NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    usage_date DATE NOT NULL,
    spending_analysis_requests INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (household_id, usage_date),
    CONSTRAINT ai_daily_usage_spending_analysis_requests_check
        CHECK (spending_analysis_requests >= 0)
);

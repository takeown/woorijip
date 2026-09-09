CREATE TABLE capture_batches (
    household_id BIGINT NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    request_id UUID NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    saved_count INTEGER NOT NULL CHECK (saved_count BETWEEN 1 AND 50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (household_id, request_id)
);

ALTER TABLE ai_daily_usage ADD COLUMN capture_requests INTEGER NOT NULL DEFAULT 0 CHECK (capture_requests >= 0);

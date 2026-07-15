CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    merchant VARCHAR(200) NOT NULL CHECK (btrim(merchant) <> ''),
    amount BIGINT NOT NULL CHECK (amount > 0),
    category VARCHAR(100) NOT NULL CHECK (btrim(category) <> ''),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX transactions_occurred_at_idx
    ON transactions (occurred_at DESC, id DESC);

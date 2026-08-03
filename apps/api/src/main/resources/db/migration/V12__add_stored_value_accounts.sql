CREATE TABLE stored_value_accounts (
    id BIGSERIAL PRIMARY KEY,
    household_id BIGINT NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    type VARCHAR(40) NOT NULL CHECK (type IN ('ONNURI_GIFT_CERTIFICATE', 'PREGNANCY_VOUCHER')),
    name VARCHAR(100) NOT NULL CHECK (btrim(name) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT stored_value_accounts_household_type_key UNIQUE (household_id, type)
);

ALTER TABLE transactions
    DROP CONSTRAINT transactions_payment_method_check,
    DROP CONSTRAINT transactions_payment_details_check,
    ADD COLUMN stored_value_account_id BIGINT REFERENCES stored_value_accounts (id),
    ADD CONSTRAINT transactions_payment_method_check
        CHECK (payment_method IN ('CARD', 'CASH', 'QR', 'UNKNOWN')),
    ADD CONSTRAINT transactions_payment_details_check
        CHECK (
            (payment_method = 'CARD' AND card_issuer IS NOT NULL)
            OR (payment_method IN ('CASH', 'QR', 'UNKNOWN') AND card_issuer IS NULL)
        );

CREATE INDEX transactions_stored_value_account_id_idx
    ON transactions (stored_value_account_id)
    WHERE stored_value_account_id IS NOT NULL;

CREATE TABLE stored_value_movements (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES stored_value_accounts (id) ON DELETE CASCADE,
    transaction_id BIGINT REFERENCES transactions (id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL CHECK (type IN ('CREDIT', 'SPEND', 'ADJUSTMENT', 'OPENING_BALANCE')),
    balance_delta BIGINT NOT NULL CHECK (balance_delta <> 0),
    paid_amount BIGINT NOT NULL DEFAULT 0 CHECK (paid_amount >= 0),
    source_name VARCHAR(100) CHECK (source_name IS NULL OR btrim(source_name) <> ''),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT stored_value_movements_transaction_key UNIQUE (transaction_id),
    CONSTRAINT stored_value_movements_details_check CHECK (
        (type = 'CREDIT' AND transaction_id IS NULL AND balance_delta > 0 AND paid_amount <= balance_delta)
        OR (type = 'SPEND' AND transaction_id IS NOT NULL AND balance_delta < 0 AND paid_amount = 0)
        OR (type IN ('ADJUSTMENT', 'OPENING_BALANCE') AND transaction_id IS NULL AND paid_amount = 0)
    )
);

CREATE INDEX stored_value_movements_account_occurred_at_idx
    ON stored_value_movements (account_id, occurred_at DESC, id DESC);

ALTER TABLE card_statement_candidates
    ADD COLUMN stored_value_account_type VARCHAR(40),
    ADD CONSTRAINT card_statement_candidates_stored_value_account_type_check
        CHECK (
            stored_value_account_type IS NULL
            OR stored_value_account_type IN ('ONNURI_GIFT_CERTIFICATE', 'PREGNANCY_VOUCHER')
        );

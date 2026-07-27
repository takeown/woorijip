CREATE TABLE card_statement_imports (
    id BIGSERIAL PRIMARY KEY,
    household_id BIGINT NOT NULL,
    payer_id BIGINT NOT NULL,
    card_issuer VARCHAR(30) NOT NULL,
    statement_month DATE NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    total_count INTEGER NOT NULL CHECK (total_count >= 0),
    total_billed_amount BIGINT NOT NULL,
    adjustment_count INTEGER NOT NULL CHECK (adjustment_count >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT card_statement_imports_household_payer_fk
        FOREIGN KEY (household_id, payer_id)
        REFERENCES household_memberships (household_id, user_id),
    CONSTRAINT card_statement_imports_fingerprint_key
        UNIQUE (household_id, payer_id, card_issuer, fingerprint)
);

CREATE INDEX card_statement_imports_household_month_idx
    ON card_statement_imports (household_id, statement_month DESC, id DESC);

CREATE TABLE card_statement_candidates (
    id BIGSERIAL PRIMARY KEY,
    import_id BIGINT NOT NULL REFERENCES card_statement_imports (id) ON DELETE CASCADE,
    source_row INTEGER NOT NULL CHECK (source_row > 0),
    occurred_on DATE NOT NULL,
    card_label VARCHAR(100) NOT NULL CHECK (btrim(card_label) <> ''),
    merchant VARCHAR(200) NOT NULL CHECK (btrim(merchant) <> ''),
    approved_amount BIGINT NOT NULL,
    billed_amount BIGINT NOT NULL,
    interest_amount BIGINT NOT NULL,
    entry_type VARCHAR(20) NOT NULL
        CHECK (entry_type IN ('PURCHASE', 'REVERSAL', 'FEE', 'INSTALLMENT')),
    installment_months INTEGER,
    installment_sequence INTEGER,
    remaining_installments INTEGER,
    remaining_principal BIGINT,
    applied_transaction_id BIGINT REFERENCES transactions (id),
    CONSTRAINT card_statement_candidates_import_row_key UNIQUE (import_id, source_row)
);

CREATE INDEX card_statement_candidates_import_idx
    ON card_statement_candidates (import_id, source_row);

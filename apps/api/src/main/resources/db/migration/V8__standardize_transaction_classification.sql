ALTER TABLE transactions
    RENAME COLUMN category TO legacy_category;

ALTER TABLE transactions
    ALTER COLUMN legacy_category DROP NOT NULL,
    ADD COLUMN category VARCHAR(30),
    ADD COLUMN classification_source VARCHAR(30) NOT NULL DEFAULT 'MIGRATION',
    ADD COLUMN classification_confidence VARCHAR(10) NOT NULL DEFAULT 'LOW',
    ADD COLUMN classification_confirmed_at TIMESTAMPTZ,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE transactions
SET category = CASE btrim(legacy_category)
        WHEN '식비' THEN 'FOOD'
        WHEN '주거' THEN 'HOUSING'
        WHEN '교통' THEN 'TRANSPORT'
        WHEN '생활' THEN 'LIVING'
        WHEN '건강' THEN 'HEALTH'
        WHEN '여가' THEN 'LEISURE'
        WHEN '교육' THEN 'EDUCATION'
        WHEN '금융·보험' THEN 'FINANCE_INSURANCE'
        WHEN '금융/보험' THEN 'FINANCE_INSURANCE'
        WHEN '경조사' THEN 'FAMILY_EVENT'
        WHEN '기타' THEN 'OTHER'
        ELSE 'OTHER'
    END,
    classification_confirmed_at = created_at;

ALTER TABLE transactions
    ALTER COLUMN category SET NOT NULL,
    ALTER COLUMN classification_source DROP DEFAULT,
    ALTER COLUMN classification_confidence DROP DEFAULT,
    ADD CONSTRAINT transactions_standard_category_check
        CHECK (
            category IN (
                'FOOD',
                'HOUSING',
                'TRANSPORT',
                'LIVING',
                'HEALTH',
                'LEISURE',
                'EDUCATION',
                'FINANCE_INSURANCE',
                'FAMILY_EVENT',
                'OTHER'
            )
        ),
    ADD CONSTRAINT transactions_classification_source_check
        CHECK (
            classification_source IN (
                'USER',
                'MERCHANT_RULE',
                'HISTORY',
                'AI',
                'MIGRATION'
            )
        ),
    ADD CONSTRAINT transactions_classification_confidence_check
        CHECK (classification_confidence IN ('HIGH', 'MEDIUM', 'LOW'));

CREATE TABLE transaction_tags (
    transaction_id BIGINT NOT NULL
        REFERENCES transactions (id)
        ON DELETE CASCADE,
    tag VARCHAR(30) NOT NULL,
    PRIMARY KEY (transaction_id, tag),
    CONSTRAINT transaction_tags_tag_check
        CHECK (tag IN ('SUBSCRIPTION', 'UTILITY', 'RECURRING_PAYMENT'))
);

CREATE INDEX transaction_tags_tag_transaction_idx
    ON transaction_tags (tag, transaction_id);

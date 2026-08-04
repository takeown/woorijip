ALTER TABLE stored_value_accounts
    DROP CONSTRAINT stored_value_accounts_type_check,
    DROP CONSTRAINT stored_value_accounts_household_owner_type_key,
    ADD COLUMN category VARCHAR(40),
    ADD COLUMN automation_key VARCHAR(40),
    ADD COLUMN archived_at TIMESTAMPTZ;

UPDATE stored_value_accounts
SET category = CASE type
        WHEN 'ONNURI_GIFT_CERTIFICATE' THEN 'GIFT_CERTIFICATE'
        WHEN 'PREGNANCY_VOUCHER' THEN 'VOUCHER'
    END,
    automation_key = type;

ALTER TABLE stored_value_accounts
    ALTER COLUMN category SET NOT NULL,
    ADD CONSTRAINT stored_value_accounts_category_check
        CHECK (category IN ('GIFT_CERTIFICATE', 'VOUCHER', 'LOCAL_CURRENCY', 'PREPAID', 'OTHER')),
    ADD CONSTRAINT stored_value_accounts_automation_key_check
        CHECK (
            automation_key IS NULL
            OR automation_key IN ('ONNURI_GIFT_CERTIFICATE', 'PREGNANCY_VOUCHER')
        ),
    ADD CONSTRAINT stored_value_accounts_automation_category_check
        CHECK (
            (automation_key = 'ONNURI_GIFT_CERTIFICATE' AND category = 'GIFT_CERTIFICATE')
            OR (automation_key = 'PREGNANCY_VOUCHER' AND category = 'VOUCHER')
            OR automation_key IS NULL
        ),
    DROP COLUMN type;

CREATE UNIQUE INDEX stored_value_accounts_active_automation_key_idx
    ON stored_value_accounts (household_id, owner_user_id, automation_key)
    WHERE automation_key IS NOT NULL AND archived_at IS NULL;

ALTER TABLE card_statement_candidates
    DROP CONSTRAINT card_statement_candidates_stored_value_account_type_check;

ALTER TABLE card_statement_candidates
    RENAME COLUMN stored_value_account_type TO stored_value_automation_key;

ALTER TABLE card_statement_candidates
    ADD CONSTRAINT card_statement_candidates_stored_value_automation_key_check
        CHECK (
            stored_value_automation_key IS NULL
            OR stored_value_automation_key IN ('ONNURI_GIFT_CERTIFICATE', 'PREGNANCY_VOUCHER')
        );

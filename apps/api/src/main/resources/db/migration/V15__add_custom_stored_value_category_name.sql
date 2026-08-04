ALTER TABLE stored_value_accounts
    ADD COLUMN custom_category_name VARCHAR(40);

UPDATE stored_value_accounts
SET custom_category_name = '기타'
WHERE category = 'OTHER';

ALTER TABLE stored_value_accounts
    ADD CONSTRAINT stored_value_accounts_custom_category_name_check
        CHECK (
            (category = 'OTHER' AND custom_category_name IS NOT NULL AND btrim(custom_category_name) <> '')
            OR (category <> 'OTHER' AND custom_category_name IS NULL)
        );

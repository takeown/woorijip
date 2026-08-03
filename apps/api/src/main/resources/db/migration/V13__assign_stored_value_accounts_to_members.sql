ALTER TABLE stored_value_accounts
    ADD COLUMN owner_user_id BIGINT;

UPDATE stored_value_accounts AS account
SET owner_user_id = (
    SELECT membership.user_id
    FROM household_memberships AS membership
    WHERE membership.household_id = account.household_id
    ORDER BY membership.id
    LIMIT 1
);

ALTER TABLE stored_value_accounts
    ALTER COLUMN owner_user_id SET NOT NULL,
    DROP CONSTRAINT stored_value_accounts_household_type_key,
    ADD CONSTRAINT stored_value_accounts_household_owner_type_key
        UNIQUE (household_id, owner_user_id, type),
    ADD CONSTRAINT stored_value_accounts_household_owner_fkey
        FOREIGN KEY (household_id, owner_user_id)
        REFERENCES household_memberships (household_id, user_id);

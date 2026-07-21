ALTER TABLE transactions
    ADD COLUMN household_id BIGINT,
    ADD COLUMN payer_id BIGINT;

UPDATE transactions
SET household_id = bootstrap_member.household_id,
    payer_id = bootstrap_member.user_id
FROM (
    SELECT household_id, user_id
    FROM household_memberships
    ORDER BY household_id, id
    LIMIT 1
) AS bootstrap_member;

ALTER TABLE transactions
    ALTER COLUMN household_id SET NOT NULL,
    ALTER COLUMN payer_id SET NOT NULL,
    ADD CONSTRAINT transactions_household_payer_fk
        FOREIGN KEY (household_id, payer_id)
        REFERENCES household_memberships (household_id, user_id);

DROP INDEX transactions_occurred_at_idx;

CREATE INDEX transactions_household_occurred_at_idx
    ON transactions (household_id, occurred_at DESC, id DESC);

CREATE INDEX transactions_household_payer_occurred_at_idx
    ON transactions (household_id, payer_id, occurred_at DESC, id DESC);

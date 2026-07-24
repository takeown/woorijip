ALTER TABLE transactions
    ADD COLUMN description VARCHAR(500),
    ADD CONSTRAINT transactions_description_check
        CHECK (description IS NULL OR btrim(description) <> '');

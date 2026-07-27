ALTER TABLE card_statement_candidates
    DROP CONSTRAINT card_statement_candidates_applied_transaction_id_fkey,
    ADD CONSTRAINT card_statement_candidates_applied_transaction_fk
        FOREIGN KEY (applied_transaction_id)
        REFERENCES transactions (id)
        ON DELETE SET NULL;

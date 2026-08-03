ALTER TABLE transactions
    DROP CONSTRAINT transactions_standard_category_check,
    ADD CONSTRAINT transactions_standard_category_check
        CHECK (
            category IN (
                'FOOD', 'HOUSING', 'TRANSPORT', 'LIVING', 'CHILDCARE', 'HEALTH',
                'LEISURE', 'EDUCATION', 'FINANCE_INSURANCE', 'FAMILY_EVENT', 'OTHER'
            )
        );

ALTER TABLE merchant_classification_rules
    DROP CONSTRAINT merchant_classification_rules_category_check,
    ADD CONSTRAINT merchant_classification_rules_category_check
        CHECK (
            category IN (
                'FOOD', 'HOUSING', 'TRANSPORT', 'LIVING', 'CHILDCARE', 'HEALTH',
                'LEISURE', 'EDUCATION', 'FINANCE_INSURANCE', 'FAMILY_EVENT', 'OTHER'
            )
        );

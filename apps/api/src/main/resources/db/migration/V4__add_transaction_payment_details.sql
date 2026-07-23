ALTER TABLE transactions
    ADD COLUMN payment_method VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN card_issuer VARCHAR(30),
    ADD CONSTRAINT transactions_payment_method_check
        CHECK (payment_method IN ('CARD', 'CASH', 'UNKNOWN')),
    ADD CONSTRAINT transactions_card_issuer_check
        CHECK (
            card_issuer IS NULL
            OR card_issuer IN (
                'LOTTE',
                'BC',
                'SAMSUNG',
                'SHINHAN',
                'WOORI',
                'HANA',
                'HYUNDAI',
                'KB_KOOKMIN',
                'NH_NONGHYUP'
            )
        ),
    ADD CONSTRAINT transactions_payment_details_check
        CHECK (
            (payment_method = 'CARD' AND card_issuer IS NOT NULL)
            OR (payment_method IN ('CASH', 'UNKNOWN') AND card_issuer IS NULL)
        );

ALTER TABLE transactions
    ALTER COLUMN payment_method DROP DEFAULT;

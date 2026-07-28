CREATE TABLE merchant_classification_rules (
    id BIGSERIAL PRIMARY KEY,
    household_id BIGINT NOT NULL
        REFERENCES households (id)
        ON DELETE CASCADE,
    normalized_merchant VARCHAR(200) NOT NULL,
    merchant_display_name VARCHAR(200) NOT NULL,
    category VARCHAR(30) NOT NULL,
    confirmed_by_user_id BIGINT NOT NULL
        REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT merchant_classification_rules_household_merchant_unique
        UNIQUE (household_id, normalized_merchant),
    CONSTRAINT merchant_classification_rules_normalized_merchant_not_blank
        CHECK (length(normalized_merchant) > 0),
    CONSTRAINT merchant_classification_rules_category_check
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
        )
);

CREATE TABLE merchant_classification_rule_tags (
    rule_id BIGINT NOT NULL
        REFERENCES merchant_classification_rules (id)
        ON DELETE CASCADE,
    tag VARCHAR(30) NOT NULL,
    PRIMARY KEY (rule_id, tag),
    CONSTRAINT merchant_classification_rule_tags_tag_check
        CHECK (tag IN ('SUBSCRIPTION', 'UTILITY', 'RECURRING_PAYMENT'))
);

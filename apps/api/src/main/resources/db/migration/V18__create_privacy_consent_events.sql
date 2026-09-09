CREATE TABLE privacy_consent_events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    consent_type VARCHAR(40) NOT NULL,
    policy_version VARCHAR(20) NOT NULL,
    agreed BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT privacy_consent_events_type_check CHECK (
        consent_type IN ('PRIVACY_POLICY', 'AI_OVERSEAS_TRANSFER')
    ),
    CONSTRAINT privacy_consent_events_version_check CHECK (btrim(policy_version) <> '')
);

CREATE INDEX privacy_consent_events_user_type_created_idx
    ON privacy_consent_events (user_id, consent_type, created_at DESC, id DESC);

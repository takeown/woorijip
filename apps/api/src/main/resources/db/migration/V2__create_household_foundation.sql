CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL CHECK (btrim(display_name) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE auth_identities (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider VARCHAR(30) NOT NULL CHECK (btrim(provider) <> ''),
    provider_subject VARCHAR(255) NOT NULL CHECK (btrim(provider_subject) <> ''),
    email VARCHAR(320) CHECK (email IS NULL OR btrim(email) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT auth_identities_provider_subject_key UNIQUE (provider, provider_subject)
);

CREATE INDEX auth_identities_user_id_idx
    ON auth_identities (user_id);

CREATE TABLE households (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL CHECK (btrim(name) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE household_memberships (
    id BIGSERIAL PRIMARY KEY,
    household_id BIGINT NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT household_memberships_household_user_key UNIQUE (household_id, user_id)
);

CREATE INDEX household_memberships_user_id_idx
    ON household_memberships (user_id);

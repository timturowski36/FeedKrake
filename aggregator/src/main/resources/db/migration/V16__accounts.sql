CREATE TABLE users (
    id                  BIGSERIAL PRIMARY KEY,
    username            VARCHAR(32) NOT NULL,
    username_ci         VARCHAR(32) NOT NULL,
    password_hash       BYTEA NOT NULL,
    password_salt       BYTEA NOT NULL,
    recovery_code_hash  BYTEA,
    recovery_code_salt  BYTEA,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_username_ci UNIQUE (username_ci)
);

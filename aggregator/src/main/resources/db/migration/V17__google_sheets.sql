CREATE TABLE user_google_tokens (
    user_id         BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    refresh_token   BYTEA NOT NULL,
    sheet_file_id   VARCHAR(128),
    sheet_file_name VARCHAR(256),
    last_synced_at  TIMESTAMPTZ,
    last_sync_error TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_sheet_events (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    row_index  INT NOT NULL,
    title      TEXT NOT NULL,
    starts_at  TIMESTAMPTZ NOT NULL,
    ends_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_user_sheet_row UNIQUE (user_id, row_index)
);
CREATE INDEX idx_user_sheet_events_user ON user_sheet_events(user_id);

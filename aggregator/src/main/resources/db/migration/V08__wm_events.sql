CREATE TABLE IF NOT EXISTS wm_events (
    id          BIGSERIAL PRIMARY KEY,
    fixture_id  INTEGER NOT NULL REFERENCES wm_fixtures(id),
    event_type  VARCHAR NOT NULL,
    player_name VARCHAR,
    team_name   VARCHAR,
    minute      INTEGER,
    detail      VARCHAR,
    fetched_at  TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_wm_events_fixture ON wm_events(fixture_id);
CREATE INDEX IF NOT EXISTS idx_wm_events_type    ON wm_events(event_type);

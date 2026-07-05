-- Vereinheitlichtes Event-Modell für die Kalenderwebsite.
-- external_id ist der natürliche Schlüssel (Quelle + Fremd-ID); sequence wird beim
-- Upsert nur erhöht, wenn sich start_time oder participants ändern (ICS SEQUENCE).
CREATE TABLE events (
    id             TEXT PRIMARY KEY,
    external_id    TEXT NOT NULL UNIQUE,
    module_type    TEXT NOT NULL,
    competition_id TEXT NOT NULL,
    title          TEXT NOT NULL,
    location       TEXT,
    start_time     TIMESTAMPTZ,
    end_time       TIMESTAMPTZ,
    status         TEXT NOT NULL,
    participants   JSONB NOT NULL DEFAULT '[]',
    metadata       JSONB NOT NULL DEFAULT '{}',
    sequence       INTEGER NOT NULL DEFAULT 0,
    last_updated   TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_events_module_start ON events (module_type, start_time);
CREATE INDEX idx_events_start        ON events (start_time);

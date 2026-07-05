-- Nutzer-Konfigurationen für den Wochenkalender: 4-stelliger Base36-Code als
-- teilbarer Schlüssel, Auswahl (Module + optionale Team-/Spieler-Refs) als JSONB.
CREATE TABLE configurations (
    code       TEXT PRIMARY KEY,
    selections JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

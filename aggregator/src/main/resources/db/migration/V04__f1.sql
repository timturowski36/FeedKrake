CREATE TABLE f1_races (
    season       INTEGER NOT NULL,
    round        INTEGER NOT NULL,
    race_name    TEXT NOT NULL,
    circuit_id   TEXT NOT NULL,
    circuit_name TEXT NOT NULL,
    country      TEXT NOT NULL,
    locality     TEXT NOT NULL,
    race_date    DATE NOT NULL,
    race_time    TIME,
    quali_date   DATE,
    quali_time   TIME,
    sprint_date  DATE,
    fp1_date     DATE,
    PRIMARY KEY (season, round)
);

CREATE TABLE f1_race_results (
    season           INTEGER NOT NULL,
    round            INTEGER NOT NULL,
    circuit_id       TEXT NOT NULL,
    position         INTEGER,
    position_text    TEXT NOT NULL,
    driver_id        TEXT NOT NULL,
    driver_code      TEXT NOT NULL,
    driver_name      TEXT NOT NULL,
    constructor_id   TEXT NOT NULL,
    constructor_name TEXT NOT NULL,
    grid             INTEGER NOT NULL,
    laps             INTEGER NOT NULL,
    status           TEXT NOT NULL,
    points           NUMERIC NOT NULL,
    fastest_lap      BOOLEAN NOT NULL DEFAULT false,
    result_type      TEXT NOT NULL,
    fetched_at       TIMESTAMP NOT NULL,
    PRIMARY KEY (season, round, driver_id, result_type)
);

CREATE TABLE f1_standings (
    season           INTEGER NOT NULL,
    round            INTEGER NOT NULL,
    standings_type   TEXT NOT NULL,
    position         INTEGER NOT NULL,
    entity_id        TEXT NOT NULL,
    entity_name      TEXT NOT NULL,
    constructor_name TEXT,
    points           NUMERIC NOT NULL,
    wins             INTEGER NOT NULL,
    fetched_at       TIMESTAMP NOT NULL,
    PRIMARY KEY (season, standings_type, entity_id)
);

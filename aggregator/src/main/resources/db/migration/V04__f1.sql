CREATE TABLE IF NOT EXISTS f1_races (
    season       INTEGER  NOT NULL,
    round        INTEGER  NOT NULL,
    race_name    VARCHAR  NOT NULL,
    circuit_id   VARCHAR  NOT NULL,
    circuit_name VARCHAR  NOT NULL,
    country      VARCHAR  NOT NULL,
    locality     VARCHAR  NOT NULL,
    race_date    DATE     NOT NULL,
    race_time    TIME,
    quali_date   DATE,
    quali_time   TIME,
    sprint_date  DATE,
    fp1_date     DATE,
    PRIMARY KEY (season, round)
);

CREATE TABLE IF NOT EXISTS f1_race_results (
    season           INTEGER  NOT NULL,
    round            INTEGER  NOT NULL,
    circuit_id       VARCHAR  NOT NULL,
    position         INTEGER,
    position_text    VARCHAR  NOT NULL,
    driver_id        VARCHAR  NOT NULL,
    driver_code      VARCHAR  NOT NULL,
    driver_name      VARCHAR  NOT NULL,
    constructor_id   VARCHAR  NOT NULL,
    constructor_name VARCHAR  NOT NULL,
    grid             INTEGER  NOT NULL,
    laps             INTEGER  NOT NULL,
    status           VARCHAR  NOT NULL,
    points           DECIMAL  NOT NULL,
    fastest_lap      BOOLEAN  NOT NULL DEFAULT false,
    result_type      VARCHAR  NOT NULL,
    fetched_at       TIMESTAMP NOT NULL,
    PRIMARY KEY (season, round, driver_id, result_type)
);

CREATE TABLE IF NOT EXISTS f1_standings (
    season           INTEGER  NOT NULL,
    round            INTEGER  NOT NULL,
    standings_type   VARCHAR  NOT NULL,
    position         INTEGER  NOT NULL,
    entity_id        VARCHAR  NOT NULL,
    entity_name      VARCHAR  NOT NULL,
    constructor_name VARCHAR,
    points           DECIMAL  NOT NULL,
    wins             INTEGER  NOT NULL,
    fetched_at       TIMESTAMP NOT NULL,
    PRIMARY KEY (season, standings_type, entity_id)
);

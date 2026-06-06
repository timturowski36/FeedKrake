CREATE TABLE IF NOT EXISTS handball_matches (
    id             BIGINT PRIMARY KEY,
    game_no        VARCHAR,
    league_id      VARCHAR NOT NULL,
    league_name    VARCHAR,
    home_team      VARCHAR NOT NULL,
    guest_team     VARCHAR NOT NULL,
    kickoff_date   VARCHAR,
    kickoff_time   VARCHAR,
    home_goals_ft  INTEGER,
    guest_goals_ft INTEGER,
    home_goals_ht  INTEGER,
    guest_goals_ht INTEGER,
    home_points    INTEGER,
    guest_points   INTEGER,
    venue_name     VARCHAR,
    venue_town     VARCHAR,
    is_finished    BOOLEAN  NOT NULL DEFAULT false,
    comment        VARCHAR  NOT NULL DEFAULT '',
    fetched_at     TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS handball_standings (
    league_id      VARCHAR  NOT NULL,
    position       INTEGER  NOT NULL,
    team_name      VARCHAR  NOT NULL,
    played         INTEGER  NOT NULL DEFAULT 0,
    won            INTEGER  NOT NULL DEFAULT 0,
    draw           INTEGER  NOT NULL DEFAULT 0,
    lost           INTEGER  NOT NULL DEFAULT 0,
    goals_for      INTEGER  NOT NULL DEFAULT 0,
    goals_against  INTEGER  NOT NULL DEFAULT 0,
    points_plus    INTEGER  NOT NULL DEFAULT 0,
    points_minus   INTEGER  NOT NULL DEFAULT 0,
    fetched_at     TIMESTAMP NOT NULL,
    PRIMARY KEY (league_id, position)
);

CREATE TABLE IF NOT EXISTS handball_ticker_events (
    match_id    BIGINT   NOT NULL,
    game_minute VARCHAR  NOT NULL,
    event_type  VARCHAR  NOT NULL,
    home_score  INTEGER,
    away_score  INTEGER,
    description VARCHAR  NOT NULL,
    fetched_at  TIMESTAMP NOT NULL,
    PRIMARY KEY (match_id, game_minute, event_type, description)
);

CREATE TABLE IF NOT EXISTS handball_scorers (
    league_id              VARCHAR   NOT NULL,
    league_name            VARCHAR,
    season                 VARCHAR,
    fetched_at             TIMESTAMP NOT NULL,
    position               INTEGER,
    player_name            VARCHAR   NOT NULL,
    team_name              VARCHAR,
    jersey_number          INTEGER,
    games_played           INTEGER,
    total_goals            INTEGER,
    field_goals            INTEGER,
    seven_meter_goals      INTEGER,
    seven_meter_attempted  INTEGER,
    seven_meter_pct        DOUBLE PRECISION,
    last_game              VARCHAR,
    goals_per_game         DOUBLE PRECISION,
    field_goals_per_game   DOUBLE PRECISION,
    warnings               INTEGER,
    two_minute_suspensions INTEGER,
    disqualifications      INTEGER
);

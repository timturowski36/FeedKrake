CREATE TABLE IF NOT EXISTS wm_teams (
    id         INTEGER PRIMARY KEY,
    name       VARCHAR   NOT NULL,
    code       VARCHAR,
    country    VARCHAR,
    logo_url   VARCHAR,
    group_name VARCHAR,
    fetched_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS wm_fixtures (
    id            INTEGER PRIMARY KEY,
    home_team_id  INTEGER,
    away_team_id  INTEGER,
    kickoff_utc   TIMESTAMP NOT NULL,
    round         VARCHAR   NOT NULL,
    group_name    VARCHAR,
    status        VARCHAR   NOT NULL,
    home_score    INTEGER,
    away_score    INTEGER,
    home_score_ht INTEGER,
    away_score_ht INTEGER,
    fetched_at    TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS wm_standings (
    team_id       INTEGER PRIMARY KEY,
    group_name    VARCHAR   NOT NULL,
    rank          INTEGER   NOT NULL,
    points        INTEGER   NOT NULL,
    played        INTEGER   NOT NULL,
    won           INTEGER   NOT NULL,
    drawn         INTEGER   NOT NULL,
    lost          INTEGER   NOT NULL,
    goals_for     INTEGER   NOT NULL,
    goals_against INTEGER   NOT NULL,
    goal_diff     INTEGER   NOT NULL,
    form          VARCHAR,
    updated_at    TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS wm_top_scorers (
    rank        INTEGER NOT NULL,
    player_name VARCHAR NOT NULL,
    team_id     INTEGER NOT NULL,
    team_name   VARCHAR NOT NULL,
    goals       INTEGER NOT NULL,
    assists     INTEGER NOT NULL,
    fetched_at  TIMESTAMP NOT NULL,
    PRIMARY KEY (player_name, team_id)
);

CREATE TABLE IF NOT EXISTS wm_sync_state (
    key        VARCHAR PRIMARY KEY,
    value      VARCHAR   NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

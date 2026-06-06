CREATE TABLE IF NOT EXISTS teams (
    id         INTEGER PRIMARY KEY,
    name       VARCHAR NOT NULL,
    short_name VARCHAR,
    icon_url   VARCHAR
);

CREATE TABLE IF NOT EXISTS matches (
    id            INTEGER PRIMARY KEY,
    league        VARCHAR NOT NULL,
    season        INTEGER NOT NULL,
    matchday      INTEGER NOT NULL,
    home_team_id  INTEGER,
    away_team_id  INTEGER,
    kickoff_at    TIMESTAMP,
    home_score_ht INTEGER,
    away_score_ht INTEGER,
    home_score_ft INTEGER,
    away_score_ft INTEGER,
    is_finished   BOOLEAN NOT NULL DEFAULT false,
    fetched_at    TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS goals (
    id          INTEGER PRIMARY KEY,
    match_id    INTEGER,
    scorer_name VARCHAR,
    minute      INTEGER,
    is_own_goal BOOLEAN NOT NULL DEFAULT false,
    is_penalty  BOOLEAN NOT NULL DEFAULT false,
    score_home  INTEGER,
    score_away  INTEGER
);

CREATE TABLE IF NOT EXISTS standings (
    league         VARCHAR  NOT NULL,
    season         INTEGER  NOT NULL,
    position       INTEGER  NOT NULL,
    team_id        INTEGER,
    played         INTEGER  NOT NULL DEFAULT 0,
    won            INTEGER  NOT NULL DEFAULT 0,
    draw           INTEGER  NOT NULL DEFAULT 0,
    lost           INTEGER  NOT NULL DEFAULT 0,
    goals_for      INTEGER  NOT NULL DEFAULT 0,
    goals_against  INTEGER  NOT NULL DEFAULT 0,
    points         INTEGER  NOT NULL DEFAULT 0,
    fetched_at     TIMESTAMP NOT NULL,
    PRIMARY KEY (league, season, position)
);

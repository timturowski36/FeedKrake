CREATE TABLE IF NOT EXISTS pubg_players (
    account_id   VARCHAR PRIMARY KEY,
    name         VARCHAR   NOT NULL,
    platform     VARCHAR   NOT NULL,
    clan_id      VARCHAR,
    ban_type     VARCHAR,
    first_seen   TIMESTAMP NOT NULL,
    last_updated TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS pubg_matches (
    match_id   VARCHAR PRIMARY KEY,
    map_name   VARCHAR,
    game_mode  VARCHAR,
    duration   INTEGER,
    created_at TIMESTAMP,
    match_type VARCHAR,
    shard_id   VARCHAR,
    fetched_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS pubg_match_participants (
    match_id         VARCHAR NOT NULL,
    account_id       VARCHAR NOT NULL,
    player_name      VARCHAR,
    kills            INTEGER,
    assists          INTEGER,
    dbnos            INTEGER,
    damage_dealt     DOUBLE PRECISION,
    headshot_kills   INTEGER,
    win_place        INTEGER,
    death_type       VARCHAR,
    time_survived    DOUBLE PRECISION,
    walk_distance    DOUBLE PRECISION,
    ride_distance    DOUBLE PRECISION,
    swim_distance    DOUBLE PRECISION,
    boosts           INTEGER,
    heals            INTEGER,
    revives          INTEGER,
    weapons_acquired INTEGER,
    kill_place       INTEGER,
    kill_streaks     INTEGER,
    longest_kill     DOUBLE PRECISION,
    PRIMARY KEY (match_id, account_id)
);

CREATE TABLE IF NOT EXISTS pubg_season_stats (
    account_id       VARCHAR NOT NULL,
    platform         VARCHAR NOT NULL,
    season_id        VARCHAR NOT NULL,
    game_mode        VARCHAR NOT NULL,
    kills            INTEGER NOT NULL DEFAULT 0,
    assists          INTEGER NOT NULL DEFAULT 0,
    dbnos            INTEGER NOT NULL DEFAULT 0,
    damage_dealt     DOUBLE PRECISION NOT NULL DEFAULT 0,
    wins             INTEGER NOT NULL DEFAULT 0,
    top10s           INTEGER NOT NULL DEFAULT 0,
    rounds_played    INTEGER NOT NULL DEFAULT 0,
    losses           INTEGER NOT NULL DEFAULT 0,
    headshot_kills   INTEGER NOT NULL DEFAULT 0,
    longest_kill     DOUBLE PRECISION NOT NULL DEFAULT 0,
    round_most_kills INTEGER NOT NULL DEFAULT 0,
    walk_distance    DOUBLE PRECISION NOT NULL DEFAULT 0,
    ride_distance    DOUBLE PRECISION NOT NULL DEFAULT 0,
    boosts           INTEGER NOT NULL DEFAULT 0,
    heals            INTEGER NOT NULL DEFAULT 0,
    revives          INTEGER NOT NULL DEFAULT 0,
    team_kills       INTEGER NOT NULL DEFAULT 0,
    fetched_at       TIMESTAMP NOT NULL,
    PRIMARY KEY (account_id, platform, season_id, game_mode)
);

CREATE INDEX IF NOT EXISTS idx_pubg_matches_created_at   ON pubg_matches (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pubg_participants_account ON pubg_match_participants (account_id);

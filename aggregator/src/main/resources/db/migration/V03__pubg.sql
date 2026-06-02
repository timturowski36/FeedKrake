CREATE TABLE pubg_players (
    account_id   TEXT PRIMARY KEY,
    name         TEXT NOT NULL,
    platform     TEXT NOT NULL,
    clan_id      TEXT,
    ban_type     TEXT,
    first_seen   TIMESTAMP NOT NULL,
    last_updated TIMESTAMP NOT NULL
);

CREATE TABLE pubg_matches (
    match_id    TEXT PRIMARY KEY,
    map_name    TEXT,
    game_mode   TEXT,
    duration    INTEGER,
    created_at  TIMESTAMP,
    match_type  TEXT,
    shard_id    TEXT,
    fetched_at  TIMESTAMP NOT NULL
);

CREATE TABLE pubg_match_participants (
    match_id         TEXT NOT NULL,
    account_id       TEXT NOT NULL,
    player_name      TEXT,
    kills            INTEGER,
    assists          INTEGER,
    dbnos            INTEGER,
    damage_dealt     DOUBLE PRECISION,
    headshot_kills   INTEGER,
    win_place        INTEGER,
    death_type       TEXT,
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

CREATE TABLE pubg_season_stats (
    account_id       TEXT NOT NULL,
    platform         TEXT NOT NULL,
    season_id        TEXT NOT NULL,
    game_mode        TEXT NOT NULL,
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

CREATE INDEX idx_pubg_matches_created_at ON pubg_matches (created_at DESC);
CREATE INDEX idx_pubg_participants_account ON pubg_match_participants (account_id);

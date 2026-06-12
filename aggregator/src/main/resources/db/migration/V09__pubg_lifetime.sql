CREATE TABLE IF NOT EXISTS pubg_lifetime_stats (
    player_name     TEXT PRIMARY KEY,
    account_id      TEXT NOT NULL,
    wins            INT NOT NULL DEFAULT 0,
    kills           INT NOT NULL DEFAULT 0,
    assists         INT NOT NULL DEFAULT 0,
    revives         INT NOT NULL DEFAULT 0,
    longest_kill    DOUBLE PRECISION NOT NULL DEFAULT 0,
    headshot_kills  INT NOT NULL DEFAULT 0,
    rounds_played   INT NOT NULL DEFAULT 0,
    top10s          INT NOT NULL DEFAULT 0,
    last_updated    TIMESTAMPTZ NOT NULL
);

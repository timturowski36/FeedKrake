-- Denormalisiertes Match-Participation-Modell (eine Zeile pro Spieler pro Match)
CREATE TABLE IF NOT EXISTS pubg_participation (
    match_id            TEXT            NOT NULL,
    player_id           TEXT            NOT NULL,
    player_name         TEXT            NOT NULL,
    match_start         TIMESTAMPTZ     NOT NULL,
    day                 DATE            NOT NULL,
    game_mode           TEXT            NOT NULL,
    map_name            TEXT            NOT NULL,
    kills               INT             NOT NULL DEFAULT 0,
    assists             INT             NOT NULL DEFAULT 0,
    dbnos               INT             NOT NULL DEFAULT 0,
    headshot_kills      INT             NOT NULL DEFAULT 0,
    damage_dealt        DOUBLE PRECISION NOT NULL DEFAULT 0,
    longest_kill        DOUBLE PRECISION NOT NULL DEFAULT 0,
    time_survived       INT             NOT NULL DEFAULT 0,
    win_place           INT             NOT NULL DEFAULT 99,
    roster_rank         INT             NOT NULL DEFAULT 0,
    revives             INT             NOT NULL DEFAULT 0,
    boosts              INT             NOT NULL DEFAULT 0,
    heals               INT             NOT NULL DEFAULT 0,
    kill_streaks        INT             NOT NULL DEFAULT 0,
    walk_distance       DOUBLE PRECISION NOT NULL DEFAULT 0,
    ride_distance       DOUBLE PRECISION NOT NULL DEFAULT 0,
    swim_distance       DOUBLE PRECISION NOT NULL DEFAULT 0,
    weapons_acquired    INT             NOT NULL DEFAULT 0,
    road_kills          INT             NOT NULL DEFAULT 0,
    vehicle_destroys    INT             NOT NULL DEFAULT 0,
    team_kills          INT             NOT NULL DEFAULT 0,
    death_type          TEXT            NOT NULL DEFAULT '',
    PRIMARY KEY (match_id, player_id)
);

-- Tages-Aggregat pro Spieler
CREATE TABLE IF NOT EXISTS pubg_player_day_stats (
    day                 DATE            NOT NULL,
    player_id           TEXT            NOT NULL,
    player_name         TEXT            NOT NULL,
    matches_played      INT             NOT NULL DEFAULT 0,
    wins                INT             NOT NULL DEFAULT 0,
    top10               INT             NOT NULL DEFAULT 0,
    total_kills         INT             NOT NULL DEFAULT 0,
    total_assists       INT             NOT NULL DEFAULT 0,
    total_damage        DOUBLE PRECISION NOT NULL DEFAULT 0,
    total_dbnos         INT             NOT NULL DEFAULT 0,
    headshot_kills      INT             NOT NULL DEFAULT 0,
    best_placement      INT             NOT NULL DEFAULT 99,
    longest_kill_day    DOUBLE PRECISION NOT NULL DEFAULT 0,
    longest_survival_day INT            NOT NULL DEFAULT 0,
    time_played_seconds INT             NOT NULL DEFAULT 0,
    avg_damage_per_match DOUBLE PRECISION NOT NULL DEFAULT 0,
    PRIMARY KEY (day, player_id)
);

-- Persönliche Rekorde pro Spieler (monoton wachsend)
CREATE TABLE IF NOT EXISTS pubg_player_records (
    player_id               TEXT            NOT NULL PRIMARY KEY,
    player_name             TEXT            NOT NULL,
    most_kills_in_match     INT             NOT NULL DEFAULT 0,
    most_kills_match_id     TEXT            NOT NULL DEFAULT '',
    longest_kill_ever       DOUBLE PRECISION NOT NULL DEFAULT 0,
    longest_kill_match_id   TEXT            NOT NULL DEFAULT '',
    longest_survival_ever   INT             NOT NULL DEFAULT 0,
    longest_survival_match_id TEXT          NOT NULL DEFAULT '',
    most_damage_in_match    DOUBLE PRECISION NOT NULL DEFAULT 0,
    most_damage_match_id    TEXT            NOT NULL DEFAULT '',
    total_chicken_dinners   INT             NOT NULL DEFAULT 0,
    most_assists_in_match   INT             NOT NULL DEFAULT 0,
    best_kill_streak        INT             NOT NULL DEFAULT 0,
    highest_dbnos_in_match  INT             NOT NULL DEFAULT 0,
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Tages-Summary für den Kalender (ein Eintrag pro Tag)
CREATE TABLE IF NOT EXISTS pubg_day_summary (
    day                     DATE            NOT NULL PRIMARY KEY,
    players_played          TEXT[]          NOT NULL DEFAULT '{}',
    total_matches           INT             NOT NULL DEFAULT 0,
    total_kills_all_players INT             NOT NULL DEFAULT 0,
    chicken_dinners         INT             NOT NULL DEFAULT 0,
    best_placement_of_day   INT             NOT NULL DEFAULT 99
);

-- Rückfüllung pubg_day_summary aus bestehenden Daten
WITH official_matches AS (
    SELECT
        m.match_id,
        (m.created_at AT TIME ZONE 'Europe/Berlin')::date AS day
    FROM pubg_matches m
    WHERE m.match_type = 'official'
)
INSERT INTO pubg_day_summary (day, players_played, total_matches, total_kills_all_players, chicken_dinners, best_placement_of_day)
SELECT
    om.day                                                   AS day,
    ARRAY(
        SELECT DISTINCT p2.player_name
        FROM pubg_match_participants p2
        JOIN official_matches om2 ON om2.match_id = p2.match_id
        WHERE om2.day = om.day
        ORDER BY p2.player_name
    )                                                        AS players_played,
    COUNT(DISTINCT om.match_id)                              AS total_matches,
    COALESCE(SUM(p.kills), 0)                                AS total_kills_all_players,
    COUNT(CASE WHEN p.win_place = 1 THEN 1 END)             AS chicken_dinners,
    MIN(p.win_place)                                         AS best_placement_of_day
FROM official_matches om
JOIN pubg_match_participants p ON p.match_id = om.match_id
GROUP BY om.day
ON CONFLICT (day) DO NOTHING;

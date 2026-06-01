package de.noonoo.adapter.config

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

object DatabaseConfig {

    private const val DB_PATH = "data/feedkrake.duckdb"

    /** Globaler Lock: DuckDB erlaubt keine parallelen Writes auf derselben Connection. */
    val lock = Any()

    fun connect(): Connection {
        File("data").mkdirs()
        return DriverManager.getConnection("jdbc:duckdb:$DB_PATH")
    }

    fun initSchema(connection: Connection) {
        connection.createStatement().use { stmt ->
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS teams (
                    id          INTEGER PRIMARY KEY,
                    name        VARCHAR NOT NULL,
                    short_name  VARCHAR,
                    icon_url    VARCHAR
                )
            """.trimIndent())

            stmt.executeUpdate("""
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
                )
            """.trimIndent())

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS goals (
                    id          INTEGER PRIMARY KEY,
                    match_id    INTEGER,
                    scorer_name VARCHAR,
                    minute      INTEGER,
                    is_own_goal BOOLEAN NOT NULL DEFAULT false,
                    is_penalty  BOOLEAN NOT NULL DEFAULT false,
                    score_home  INTEGER,
                    score_away  INTEGER
                )
            """.trimIndent())

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS standings (
                    league         VARCHAR NOT NULL,
                    season         INTEGER NOT NULL,
                    position       INTEGER NOT NULL,
                    team_id        INTEGER,
                    played         INTEGER NOT NULL DEFAULT 0,
                    won            INTEGER NOT NULL DEFAULT 0,
                    draw           INTEGER NOT NULL DEFAULT 0,
                    lost           INTEGER NOT NULL DEFAULT 0,
                    goals_for      INTEGER NOT NULL DEFAULT 0,
                    goals_against  INTEGER NOT NULL DEFAULT 0,
                    points         INTEGER NOT NULL DEFAULT 0,
                    fetched_at     TIMESTAMP NOT NULL,
                    PRIMARY KEY (league, season, position)
                )
            """.trimIndent())

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS articles (
                    url          VARCHAR PRIMARY KEY,
                    source       VARCHAR NOT NULL,
                    title        VARCHAR NOT NULL,
                    published_at TIMESTAMP,
                    fetched_at   TIMESTAMP NOT NULL
                )
            """.trimIndent())

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS pubg_players (
                    account_id   VARCHAR PRIMARY KEY,
                    name         VARCHAR NOT NULL,
                    platform     VARCHAR NOT NULL,
                    clan_id      VARCHAR,
                    ban_type     VARCHAR,
                    first_seen   TIMESTAMP NOT NULL,
                    last_updated TIMESTAMP NOT NULL
                )
            """.trimIndent())

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS pubg_matches (
                    match_id    VARCHAR PRIMARY KEY,
                    map_name    VARCHAR,
                    game_mode   VARCHAR,
                    duration    INTEGER,
                    created_at  TIMESTAMP,
                    match_type  VARCHAR,
                    shard_id    VARCHAR,
                    fetched_at  TIMESTAMP NOT NULL
                )
            """.trimIndent())

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS pubg_match_participants (
                    match_id         VARCHAR NOT NULL,
                    account_id       VARCHAR NOT NULL,
                    player_name      VARCHAR,
                    kills            INTEGER,
                    assists          INTEGER,
                    dbnos            INTEGER,
                    damage_dealt     DOUBLE,
                    headshot_kills   INTEGER,
                    win_place        INTEGER,
                    death_type       VARCHAR,
                    time_survived    DOUBLE,
                    walk_distance    DOUBLE,
                    ride_distance    DOUBLE,
                    swim_distance    DOUBLE,
                    boosts           INTEGER,
                    heals            INTEGER,
                    revives          INTEGER,
                    weapons_acquired INTEGER,
                    kill_place       INTEGER,
                    kill_streaks     INTEGER,
                    longest_kill     DOUBLE,
                    PRIMARY KEY (match_id, account_id)
                )
            """.trimIndent())

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS handball_matches (
                    id              BIGINT PRIMARY KEY,
                    game_no         VARCHAR,
                    league_id       VARCHAR NOT NULL,
                    league_name     VARCHAR,
                    home_team       VARCHAR NOT NULL,
                    guest_team      VARCHAR NOT NULL,
                    kickoff_date    VARCHAR,
                    kickoff_time    VARCHAR,
                    home_goals_ft   INTEGER,
                    guest_goals_ft  INTEGER,
                    home_goals_ht   INTEGER,
                    guest_goals_ht  INTEGER,
                    home_points     INTEGER,
                    guest_points    INTEGER,
                    venue_name      VARCHAR,
                    venue_town      VARCHAR,
                    is_finished     BOOLEAN NOT NULL DEFAULT false,
                    comment         VARCHAR NOT NULL DEFAULT '',
                    fetched_at      TIMESTAMP NOT NULL
                )
            """.trimIndent())

            // Migration für bestehende DBs ohne comment-Spalte
            stmt.executeUpdate(
                "ALTER TABLE handball_matches ADD COLUMN IF NOT EXISTS comment VARCHAR DEFAULT ''"
            )

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS handball_standings (
                    league_id     VARCHAR NOT NULL,
                    position      INTEGER NOT NULL,
                    team_name     VARCHAR NOT NULL,
                    played        INTEGER NOT NULL DEFAULT 0,
                    won           INTEGER NOT NULL DEFAULT 0,
                    draw          INTEGER NOT NULL DEFAULT 0,
                    lost          INTEGER NOT NULL DEFAULT 0,
                    goals_for     INTEGER NOT NULL DEFAULT 0,
                    goals_against INTEGER NOT NULL DEFAULT 0,
                    points_plus   INTEGER NOT NULL DEFAULT 0,
                    points_minus  INTEGER NOT NULL DEFAULT 0,
                    fetched_at    TIMESTAMP NOT NULL,
                    PRIMARY KEY (league_id, position)
                )
            """.trimIndent())

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS handball_ticker_events (
                    match_id    BIGINT NOT NULL,
                    game_minute VARCHAR NOT NULL,
                    event_type  VARCHAR NOT NULL,
                    home_score  INTEGER,
                    away_score  INTEGER,
                    description VARCHAR NOT NULL,
                    fetched_at  TIMESTAMP NOT NULL,
                    PRIMARY KEY (match_id, game_minute, event_type, description)
                )
            """.trimIndent())

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS f1_races (
                    season       INTEGER NOT NULL,
                    round        INTEGER NOT NULL,
                    race_name    VARCHAR NOT NULL,
                    circuit_id   VARCHAR NOT NULL,
                    circuit_name VARCHAR NOT NULL,
                    country      VARCHAR NOT NULL,
                    locality     VARCHAR NOT NULL,
                    race_date    DATE NOT NULL,
                    race_time    TIME,
                    quali_date   DATE,
                    quali_time   TIME,
                    sprint_date  DATE,
                    fp1_date     DATE,
                    PRIMARY KEY (season, round)
                )
            """.trimIndent())

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS f1_race_results (
                    season           INTEGER NOT NULL,
                    round            INTEGER NOT NULL,
                    circuit_id       VARCHAR NOT NULL,
                    position         INTEGER,
                    position_text    VARCHAR NOT NULL,
                    driver_id        VARCHAR NOT NULL,
                    driver_code      VARCHAR NOT NULL,
                    driver_name      VARCHAR NOT NULL,
                    constructor_id   VARCHAR NOT NULL,
                    constructor_name VARCHAR NOT NULL,
                    grid             INTEGER NOT NULL,
                    laps             INTEGER NOT NULL,
                    status           VARCHAR NOT NULL,
                    points           DECIMAL NOT NULL,
                    fastest_lap      BOOLEAN NOT NULL DEFAULT false,
                    result_type      VARCHAR NOT NULL,
                    fetched_at       TIMESTAMP NOT NULL,
                    PRIMARY KEY (season, round, driver_id, result_type)
                )
            """.trimIndent())

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS f1_standings (
                    season           INTEGER NOT NULL,
                    round            INTEGER NOT NULL,
                    standings_type   VARCHAR NOT NULL,
                    position         INTEGER NOT NULL,
                    entity_id        VARCHAR NOT NULL,
                    entity_name      VARCHAR NOT NULL,
                    constructor_name VARCHAR,
                    points           DECIMAL NOT NULL,
                    wins             INTEGER NOT NULL,
                    fetched_at       TIMESTAMP NOT NULL,
                    PRIMARY KEY (season, standings_type, entity_id)
                )
            """.trimIndent())

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS handball_scorers (
                    league_id              VARCHAR NOT NULL,
                    league_name            VARCHAR,
                    season                 VARCHAR,
                    fetched_at             TIMESTAMP NOT NULL,
                    position               INTEGER,
                    player_name            VARCHAR NOT NULL,
                    team_name              VARCHAR,
                    jersey_number          INTEGER,
                    games_played           INTEGER,
                    total_goals            INTEGER,
                    field_goals            INTEGER,
                    seven_meter_goals      INTEGER,
                    seven_meter_attempted  INTEGER,
                    seven_meter_pct        DOUBLE,
                    last_game              VARCHAR,
                    goals_per_game         DOUBLE,
                    field_goals_per_game   DOUBLE,
                    warnings               INTEGER,
                    two_minute_suspensions INTEGER,
                    disqualifications      INTEGER
                )
            """.trimIndent())

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS pubg_season_stats (
                    account_id       VARCHAR NOT NULL,
                    platform         VARCHAR NOT NULL,
                    season_id        VARCHAR NOT NULL,
                    game_mode        VARCHAR NOT NULL,
                    kills            INTEGER NOT NULL DEFAULT 0,
                    assists          INTEGER NOT NULL DEFAULT 0,
                    dbnos            INTEGER NOT NULL DEFAULT 0,
                    damage_dealt     DOUBLE  NOT NULL DEFAULT 0,
                    wins             INTEGER NOT NULL DEFAULT 0,
                    top10s           INTEGER NOT NULL DEFAULT 0,
                    rounds_played    INTEGER NOT NULL DEFAULT 0,
                    losses           INTEGER NOT NULL DEFAULT 0,
                    headshot_kills   INTEGER NOT NULL DEFAULT 0,
                    longest_kill     DOUBLE  NOT NULL DEFAULT 0,
                    round_most_kills INTEGER NOT NULL DEFAULT 0,
                    walk_distance    DOUBLE  NOT NULL DEFAULT 0,
                    ride_distance    DOUBLE  NOT NULL DEFAULT 0,
                    boosts           INTEGER NOT NULL DEFAULT 0,
                    heals            INTEGER NOT NULL DEFAULT 0,
                    revives          INTEGER NOT NULL DEFAULT 0,
                    team_kills       INTEGER NOT NULL DEFAULT 0,
                    fetched_at       TIMESTAMP NOT NULL,
                    PRIMARY KEY (account_id, platform, season_id, game_mode)
                )
            """.trimIndent())
        }
    }
}

-- Das Aggregationsmodell (Tages-Stats, Rekorde, Participation) soll nur die in
-- der Config getrackten Spieler (pubg_players) enthalten. Die Live-Ingestion
-- hat bisher die komplette Lobby jedes Matches aggregiert; die Rohdaten in
-- pubg_match_participants bleiben davon unberührt erhalten.
DELETE FROM pubg_participation p
WHERE NOT EXISTS (SELECT 1 FROM pubg_players pl WHERE pl.account_id = p.player_id);

DELETE FROM pubg_player_day_stats s
WHERE NOT EXISTS (SELECT 1 FROM pubg_players pl WHERE pl.account_id = s.player_id);

DELETE FROM pubg_player_records r
WHERE NOT EXISTS (SELECT 1 FROM pubg_players pl WHERE pl.account_id = r.player_id);

-- total_chicken_dinners wurde bisher bei jedem Backfill (jeder Aggregator-Start)
-- erneut aufaddiert statt gesetzt. Auf den echten Stand aus der Historie korrigieren.
UPDATE pubg_player_records r
SET total_chicken_dinners = (
    SELECT COUNT(*) FROM pubg_participation p
    WHERE p.player_id = r.player_id AND p.win_place = 1
);

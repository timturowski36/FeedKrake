# ADR 0001: Wetter-Datenquelle

## Status

Angenommen (umgesetzt).

## Kontext

Das Wetter-Modul (Recklinghausen/Oberhausen) benötigt eine kostenlose, ohne API-Key
nutzbare Vorhersage-Quelle mit ausreichender Genauigkeit für NRW und einem Horizont
von mehreren Tagen.

## Optionen

| Kriterium | Open-Meteo | Bright Sky |
|---|---|---|
| API-Key | keiner nötig | keiner nötig |
| Kosten | frei (nicht-kommerziell) | frei |
| Modell für DE | DWD ICON (beste NRW-Genauigkeit) | DWD-Rohdaten |
| Forecast-Horizont | bis 16 Tage | ~10 Tage |
| Format | JSON, `timezone`-Param | JSON |
| Limit | ~10.000 Calls/Tag | fair use |

## Entscheidung

Open-Meteo (`https://api.open-meteo.com/v1/forecast`) als primäre Quelle. Bei 2 Orten
× stündlichem Poll ergeben sich 48 Calls/Tag, unter 1 % des Limits. Bright Sky bleibt
als dokumentierter Fallback, falls Open-Meteo dauerhaft ausfällt — aktuell nicht
implementiert, da kein Ausfall aufgetreten ist.

Wichtig beim Parsen: Bei `timezone=Europe/Berlin` liefert Open-Meteo lokale Zeiten
ohne Offset-Suffix (z. B. `2026-07-06T14:00`) — das Mapping interpretiert diese
Zeiten explizit als `Europe/Berlin`, nicht als UTC (siehe `OpenMeteoAdapter.kt`).

## Konsequenzen

- Vergangene Tage zeigen den letzten gespeicherten Vorhersagestand, keine gemessenen
  Ist-Werte (siehe `PostgresWeatherRepository`: Upsert schreibt vergangene Tage nicht
  mehr um).
- Bei Kommerzialisierung des Projekts wäre ein kostenpflichtiger Open-Meteo-Plan
  nötig (Lizenz ist frei nur für nicht-kommerzielle Nutzung).

# NooNoo-Web: Änderungsplan

## 1. Head-to-Head-Statistik für Bundesliga (1. & 2. Liga)

- In der Detailansicht eines **zukünftigen** Spiels: Anzeige der **letzten 5 Begegnungen** zwischen den beiden Teams.
- Pro vergangener Begegnung: Datum, Ergebnis, Wettbewerb (Liga/Pokal, falls unterscheidbar).
- Datenquelle: bestehende API-Anbindung (z. B. API-Football/football-data.org) prüfen, ob Head-to-Head-Endpoint vorhanden ist oder ob aus historischen Spielergebnissen in der eigenen DB berechnet werden muss.
- Gilt für 1. und 2. Bundesliga gleichermaßen.

## 2. Regelmäßige Terminüberprüfung (Anstoßzeiten)

- Hintergrund: Spiele werden teils vorläufig mit Platzhalterzeit (z. B. 11:00 Uhr) angelegt und später final terminiert.
- Neuer täglicher Job, der bereits gespeicherte, aber noch nicht gestartete Spiele erneut gegen die Datenquelle abgleicht und bei Abweichung Datum/Uhrzeit aktualisiert.
- Betrifft alle Module mit vorterminierten Spielen (primär Bundesliga 1/2, ggf. auch WM).
- Zu klären beim Bau: welches Zeitfenster geprüft wird (z. B. alle Spiele der nächsten X Wochen) und ob Änderungen geloggt/sichtbar gemacht werden sollen.

## 3. Account-Bereich

- Registrierung: Benutzername + Passwort, keine E-Mail-Pflicht, keine E-Mail-Bestätigung.
- Passwort/Benutzername vergessen → **kein Reset-Flow**, Nutzer muss sich neu registrieren.
  - Zu klären: Soll der alte Account (inkl. hinterlegter Google-Sheets-Verbindung und eigener Termine) dann verwaist in der DB bleiben oder nach einer gewissen Zeit automatisch bereinigt werden?
- Erstes freischaltbares Modul nach Login: eigene Termine über Google Sheets.

## 4. Modul „Eigene Termine" via Google Sheets (echte API-Anbindung)

- Echte OAuth2-Anbindung an das Google-Konto des Nutzers (nicht nur ein internes Modul mit dem Namen „Google Sheets").
- Nutzer verbindet sein Google-Konto, wählt/erstellt ein Sheet mit eigenen Terminen.
- Erwartete Spalten: **Von**, **Bis**, **Titel**. Sonst nichts.
- Regel: Ist nur **Von** gesetzt (kein Bis), wird der Termin standardmäßig mit **1 Stunde Dauer** angezeigt.
- Offene Punkte, die vor der Umsetzung geklärt werden sollten:
  - Google Cloud Projekt / OAuth-Consent-Screen vorhanden, oder muss das neu eingerichtet werden (inkl. Verifizierung falls nötig)?
  - Soll das Sheet vom Nutzer frei benannt/ausgewählt werden, oder erstellt NooNoo automatisch ein Sheet mit fixem Namen/Format im Google Drive des Nutzers?
  - Wie oft wird das Sheet synchronisiert (Polling-Intervall) oder passiert das nur bei Seitenaufruf?
  - Wo/wie werden die OAuth-Tokens der Nutzer sicher gespeichert (Verschlüsselung in der bestehenden PostgreSQL-DB)?

## 5. Reihenfolge / Abhängigkeiten

1. Account-System (Registrierung/Login) – Grundvoraussetzung für Modul 4
2. Google Sheets OAuth-Anbindung + eigenes Terminmodul
3. Head-to-Head-Statistik Bundesliga
4. Täglicher Termin-Check-Job

Diese Reihenfolge ist ein Vorschlag – bitte prüfen, ob Priorität anders gewünscht ist, bevor es an Claude Code geht.

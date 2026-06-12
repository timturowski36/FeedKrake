# Quellen für deutsche Quizfragen (Allgemeinwissen) für einen Stream-Button

## TL;DR
- **Beste API-fähige Lösung auf Deutsch:** Es gibt keine perfekte, große, komplett kostenlose deutsche Quiz-API. Der pragmatischste Weg ist **The Trivia API** (Übersetzungen nur im kostenpflichtigen „Complete"-Plan — Deutsch-Verfügbarkeit vor Vertrag verifizieren) oder **opentrivia.de** (kostenlose JSON-API mit maschinell übersetzten OpenTDB-Fragen, aber sehr kleiner Bestand). Wichtig: **OpenTDB selbst ist ausschließlich Englisch.**
- **Für sofort nutzbare große Mengen ohne API:** Lade fertige deutsche Datensätze von GitHub herunter (z.B. `nicoruti/quizfragen` als `aggregated.json` mit 4 Antwortmöglichkeiten + Schwierigkeit, oder `MacManus88/Linux-Quiz` als CSV). Diese sind maschinenlesbar und kostenlos.
- **Für einen Stream-Button empfehle ich**, einen eigenen lokalen Fragenpool (JSON/CSV) aus mehreren der unten genannten freien Datensätze aufzubauen statt sich auf eine Live-API zu verlassen — das ist zuverlässiger, offline-fähig und rechtlich am saubersten (Lizenzen beachten).

## Key Findings
1. **Die populärste Trivia-API (OpenTDB) bietet KEINE deutschen Fragen.** Sie ist explizit nur auf Englisch verfügbar. Wer „Open Trivia DB mit German locale" sucht, wird enttäuscht — diese Option existiert nicht.
2. **The Trivia API** bietet Übersetzungen, aber nur im kostenpflichtigen Abo. Die offizielle FAQ listet Französisch, Hindi, Spanisch, Niederländisch und Türkisch — **Deutsch wird dort nicht explizit genannt** (nur ein Drittartikel führt Deutsch auf). Die kostenlose Stufe ist Englisch und CC-BY-NC (nur nicht-kommerziell).
3. **opentrivia.de** ist ein deutsches OpenTDB-Pendant mit kostenloser JSON-API und CC-BY-SA-Lizenz, aber sehr kleinem Bestand (901 Fragen, davon nur 4 geprüft, Stand 9. Juni 2026) — noch „im Aufbau".
4. **Quizolai.de** ist die einzige speziell für den deutschen Markt gebaute Quiz-API/Datenbank, aber kommerziell (mehrere Tarife) und mit dünner öffentlicher Dokumentation.
5. **Fertige deutsche Datensätze (GitHub/Kaggle/HuggingFace)** sind die zuverlässigste kostenlose Quelle für große Mengen maschinenlesbarer deutscher Fragen mit Kategorien.

## Details

### 1. APIs

**Open Trivia DB (opentdb.com)**
- URL: https://opentdb.com/ , API-Doku: https://opentdb.com/api_config.php
- API: Ja — kostenlose JSON-REST-API, kein API-Key nötig. Endpoint: `https://opentdb.com/api.php?amount=10&category=9&difficulty=medium&type=multiple`
- Kategorien: 24 aktive Kategorien (General Knowledge, Science, History, Geography, Sports, Music, Film, Nature/Animals, etc.)
- Kosten: Komplett kostenlos. Lizenz CC-BY-SA 4.0.
- Sprache: **Nur Englisch.** Keine deutschen Fragen.
- Qualität & Umfang: Community-erstellt, geprüft; „over 4,000 verified trivia questions across 24 categories" (Apify-Scraper-Beschreibung) — ein konkreter Vollexport (GitHub QuartzWarrior/OTDB-Source) nennt **4.738 Fragen**. Rate-Limit: 1 Request pro 5 Sekunden pro IP, max. 50 Fragen pro Call. Session-Tokens verhindern Wiederholungen.
- Zugriff: Direkt per HTTP-GET; antwortet ggf. base64-/URL-codiert.
- **Bewertung für dein Projekt:** Technisch ideal (kostenlos, kein Key, Session-Tokens), aber nur brauchbar, wenn du die Fragen selbst ins Deutsche übersetzt.

**The Trivia API (the-trivia-api.com)**
- URL: https://the-trivia-api.com/ , Doku: https://the-trivia-api.com/docs/v2/
- API: Ja — REST/JSON. Endpoint: `GET https://the-trivia-api.com/v2/questions`. Free Tier ohne Account nutzbar; erweiterte Features per `x-api-key`.
- Kategorien: 10 Kategorien inkl. Science, History, Geography, Music, Film & TV, Sport & Leisure, Arts & Literature, Food & Drink, Society & Culture, General Knowledge.
- Kosten: Kostenlos für **nicht-kommerzielle** Nutzung. Wörtlich (FAQ): „The API and all data returned from it are licensed under a Creative Commons Attribution-NonCommercial 4.0 International License. This means that the API and the questions it returns are free for noncommercial use." Kommerzielle Nutzung und Premium-Features (inkl. Übersetzungen) nur per kostenpflichtigem Abo.
- Sprache: Englisch standardmäßig; **Übersetzungen** als Premium-Feature. **Vorsicht:** Die offizielle FAQ nennt wörtlich nur „French, Hindi, Spanish, Dutch & Turkish" — **Deutsch ist dort nicht aufgeführt.** Lediglich ein Drittartikel (apileague.com) erwähnt „professional translations into languages like Spanish, French, German, Hindi, Dutch, and Turkish". **Deutsch-Verfügbarkeit daher unbedingt vor einem Abo direkt beim Anbieter verifizieren.**
- Qualität: Hochwertig, gut kategorisiert, Tags, Schwierigkeit, Session-Management gegen Wiederholungen, Bildfragen (Premium).
- Zugriff: HTTP-GET mit Query-Parametern; mehrsprachige Ausgabe erfordert „Complete"-Subscription.
- **Bewertung:** Die etablierteste, am besten dokumentierte API mit (laut Drittquelle) deutschen Übersetzungen — aber kostenpflichtig für Übersetzungen/kommerziell, und die offizielle Deutsch-Bestätigung fehlt.

**opentrivia.de**
- URL: https://opentrivia.de/ , API: https://opentrivia.de/api , Browser: https://opentrivia.de/browse
- API: Ja — „völlig kostenlose JSON-API", kein API-Key laut Eigenbeschreibung (analog OpenTDB).
- Kategorien: Übernommen aus OpenTDB (übersetzt).
- Kosten: Kostenlos, CC-BY-SA 4.0.
- Sprache: **Deutsch** — teils maschinell übersetzte OpenTDB-Fragen, teils nutzergeneriert.
- Qualität & Umfang: Direkt verifiziert (opentrivia.de/browse, Stand 9. Juni 2026): **901 Fragen, Seite 1/37**, davon nur 4 geprüft. Übersetzungsqualität schwankend — der erste Eintrag ist eine erkennbar maschinell übersetzte OpenTDB-Frage: „Saul Hudson (Slash) der Band Guns N 'Roses ist dafür bekannt, welche Art von Gitarre zu spielen?". Seite ist „im Aufbau".
- **Bewertung:** Vielversprechendes Konzept und die einzige wirklich kostenlose deutsche JSON-API, aber aktuell zu klein und Übersetzungsqualität teils holprig. Für einen abwechslungsreichen Stream nicht ausreichend als alleinige Quelle.

**Quizolai (quizolai.de)**
- URL: https://quizolai.de/ , Doku: https://docs.quizolai.de/
- API: Ja — REST/JSON. Bestätigter Endpoint: „question of the day" (https://docs.quizolai.de/docs/quizolai/get-question-of-the-day/), Response-Schema mit Pflichtfeldern `id` (number) und `questionText` (string). Basis-URL und vollständige Endpunktliste sind öffentlich nicht dokumentiert.
- Kategorien: „verschiedene Kategorien und Schwierigkeitsgrade" (konkrete Liste nicht öffentlich dokumentiert).
- Kosten: Kommerziell — „Wählen Sie den passenden Tarif für Ihren Zugang zu unserer Quiz-API." Ob ein kostenloser Tarif existiert und die genauen Preise sind öffentlich nicht ermittelbar (Seite blockiert automatisierten Zugriff, Detailseiten kaum indexiert).
- Sprache: **Deutsch** — explizit „auf den deutschen Markt ausgerichtet, mit einer großen Auswahl an deutschsprachigen Fragen"; Community- + KI-kuratiert.
- Qualität: Eigenangabe „eine der größten Quizfragen-Datenbanken im Web"; KI-Qualitätsprüfung der eingereichten Fragen.
- **Bewertung:** Einzige speziell deutsche (native, nicht übersetzte) kommerzielle Quiz-API. Prüfenswert, wenn Budget vorhanden — aber wegen intransparenter Doku/Preise vorab direkt Kontakt aufnehmen.

**API Ninjas Trivia (api-ninjas.com/api/trivia)**
- API: Ja, `X-Api-Key` nötig. Wörtlich: „Free users have access to 100 trivia questions - premium users have access to over 100,000 trivia questions." Kategorien inkl. music, history, science, geography. **Nur Englisch.**

### 2. Kostenlose Datenbanken / Datensätze (Download, JSON/CSV)

**nicoruti/quizfragen (GitHub)**
- URL: https://github.com/nicoruti/quizfragen — Datei: `src/main/resources/aggregated.json`
- Format: JSON, jede Frage mit 4 Antwortmöglichkeiten + Schwierigkeit (Leicht/Mittel/Schwer).
- Sprache: **Deutsch.** Kategorien: Geschichte, Geografie, Naturwissenschaften, Sport, Musik, u.a.
- Kosten: Kostenlos (aus diversen Quellen aggregiert).
- **Bewertung:** Sehr gut geeignet als direkter Fragenpool für einen Stream-Button — fertig strukturiert.

**MacManus88/Linux-Quiz (GitHub)**
- URL: https://github.com/MacManus88/Linux-Quiz — Datei: `quiz-fragen.csv`
- Format: CSV (Frage; 4 Antworten; Kategorie; Schwierigkeit 1-5).
- Sprache: **Deutsch.** Kategorien: Geschichte, Sport, Naturwissenschaften, u.a.
- Kosten: Kostenlos.
- **Bewertung:** Ideal maschinenlesbar; überlappt inhaltlich mit vielen anderen deutschen Sammlungen (selber Ursprungspool).

**casparjones/First-Ruby-Steps — quiz/quiz-fragen.csv (GitHub)**
- URL: https://github.com/casparjones/First-Ruby-Steps/blob/master/quiz/quiz-fragen.csv
- Format: CSV, Deutsch. Kostenlos.

**OpenTriviaQA (uberspot/OpenTriviaQA, GitHub)**
- URL: https://github.com/uberspot/OpenTriviaQA — CC-BY-SA 4.0, leicht zu JSON parsbar. **Nur Englisch**, aber als Basis für eigene Übersetzung nutzbar.

**Kaggle / HuggingFace**
- Open Trivia DB Dataset (Kaggle): https://www.kaggle.com/datasets/shreyasur965/open-trivia-database-quiz-questions-all-categories — CSV, **Englisch**.
- 200.000+ Jeopardy Questions (Kaggle) — CSV, Englisch.
- HuggingFace `deepset/germanquad` — deutsches QA-Dataset (eher Reading-Comprehension als Quiz, aber deutschsprachig).

### 3. Webseiten mit großen deutschen Fragenpools (keine offizielle API, aber große Mengen)

**Fragespiel.com**
- URL: https://www.fragespiel.com/quizfragen/ — Eigenangabe: „Über 13.000 Quiz-Fragen online spielen ➤ ohne Anmeldung auf Fragespiel.com" mit „5 Schwierigkeitsstufen". Viele Kategorien (Geografie, Deutschland, Politik, Physik, Astronomie, u.a.). Kostenlos spielbar. **Keine offizielle API** — nur Web-Oberfläche.

**Forschung-und-Wissen.de Quiz**
- URL: https://www.forschung-und-wissen.de/quiz/ — tausende Fragen, viele Kategorien, wöchentlich neue. Keine API.

**Quizworld.de**
- URL: https://quizworld.de/ — tägliches Quiz, Kategorien Geschichte, Politik, Musik, Literatur, Geografie, Biologie. Keine API.

**wissens-quiz.freenet.de** — 7.500 Fragen aus vielen Bereichen. Keine API.

**Studyflix / Karrierebibel** — kuratierte Listen (50-100 Fragen) mit Antworten, gut für manuelle Befüllung.

**QuizPro-Fragenkataloge (litschi.de)** — Ältere Windows-Freeware mit umfangreichen, von Nutzern erstellten `.qiz`-Fragenkatalogen (ca. 80-MB-ZIP-Download), parsbar (CSV-artig, simple ASCII-Verschlüsselung -3). Deutsch, viele Themen. Für Bastler eine große kostenlose Quelle.

**Hinweis zu diesen Webseiten:** Da sie keine API anbieten, müsstest du die Fragen manuell sammeln oder scrapen. Beim Scrapen unbedingt Urheberrecht/Nutzungsbedingungen beachten — die meisten dieser Seiten erlauben keine Weiterverwertung.

## Recommendations

**Stufe 1 (sofort, kostenlos, empfohlen):** Baue einen lokalen JSON-Fragenpool aus den GitHub-Datensätzen `nicoruti/quizfragen` (aggregated.json) und `MacManus88/Linux-Quiz` (CSV). Beide sind deutsch, mehrkategorial, mit 4 Antworten + Schwierigkeit und sofort maschinenlesbar. Dein OBS/Twitch-Button zieht dann zufällig eine Frage aus dieser lokalen Datei — schnell, offline-fähig, kein Rate-Limit. Achte auf die jeweiligen Lizenzen (Quellen nennen).

**Stufe 2 (wenn du Live-API willst):** Teste `opentrivia.de/api` für deutsche Fragen per Live-Call. Wegen des kleinen Bestands (901 Fragen) nur als Ergänzung, nicht als Hauptquelle.

**Stufe 3 (wenn Budget & kommerzielle/professionelle Nutzung):** Abonniere **The Trivia API Complete** (Session-Management, große Kategorienauswahl) — aber **erst nach schriftlicher Bestätigung, dass Deutsch enthalten ist** — oder kontaktiere **Quizolai** für Preise und einen evtl. Free-Tier. Wähle The Trivia API, wenn du eine erprobte, dokumentierte API willst; Quizolai, wenn du nativ deutsche (statt übersetzte) Fragen bevorzugst.

**Stufe 4 (Eigenproduktion):** Kombiniere OpenTDB (englisch, kostenlos, ~4.700 Fragen) + eine Übersetzungs-API (DeepL/LLM), um automatisiert einen großen deutschen Pool zu erzeugen. Manuell nachprüfen.

**Benchmarks, die deine Entscheidung ändern:** Wenn dein Stream-Button mehr als ~1.000 verschiedene Fragen ohne Wiederholung braucht → lokaler Pool oder bezahlte API statt opentrivia.de. Wenn die Nutzung kommerziell ist (Monetarisierung, Sponsoring) → The Trivia API Free Tier (CC-BY-NC) scheidet aus, dann bezahltes Abo oder CC-BY-SA-Datensätze.

## Caveats
- **OpenTDB hat trotz vieler anderslautender Blog-Behauptungen keine deutschen Fragen** — die deutschsprachigen „Open Trivia API"-Marketing-Artikel (allthingsdev.co etc.) beziehen sich auf die englische API mit deutscher UI-Beschreibung, nicht auf deutsche Fragen.
- **Deutsch bei The Trivia API ist nicht offiziell bestätigt:** Die FAQ nennt nur Französisch, Hindi, Spanisch, Niederländisch und Türkisch. Deutsch erscheint nur in einer Drittquelle. Vor jedem Abo direkt beim Anbieter klären.
- **Preise und Free-Tier von Quizolai** konnten nicht verifiziert werden (Seite blockiert automatisierten Zugriff, Detailseiten kaum indexiert). Vor Vertragsabschluss direkt prüfen.
- **Bestand von opentrivia.de** (901 Fragen, 9. Juni 2026) ist eine Momentaufnahme und kann wachsen.
- **Lizenzen beachten:** CC-BY-SA verlangt Namensnennung + Weitergabe unter gleicher Lizenz; CC-BY-NC verbietet kommerzielle Nutzung. Für einen monetarisierten Twitch-Stream ist das relevant.
- **Qualität maschinell übersetzter Fragen** (opentrivia.de) kann Fehler enthalten; vor Live-Einsatz stichprobenartig prüfen.
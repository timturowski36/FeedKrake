# UFC / MMA Data Sources for a NooNoo-Web UFC Module — Comprehensive Comparison

## TL;DR
- **Build the UFC module on ESPN's hidden API** (`site.api.espn.com` + `sports.core.api.espn.com`, league slug `mma/ufc`) exactly as you did the World Cup module: it's free, no API key, pure JSON, and covers fight cards, live status, results (method/round/time), and fighter profiles. Its one real gap is **structured rankings**, which ESPN does not expose as an endpoint.
- **Fill the rankings gap with a free open dataset or scraper** — the Octagon API (open source, scrapes UFC.com rankings) or a weekly `ufcstats.com`/UFC.com scrape — and add **The Odds API free tier (500 credits/month)** if you want betting odds.
- **Skip the paid majors for a hobby project**: Sportradar MMA is enterprise-priced (third-party comparisons put it at "$10,000+/mo" to the "$30K+/mo range," sales-gated with no public rate card); API-Sports MMA ($0 free / paid from $10/mo) and RapidAPI MMA listings are the only "real" paid options at hobby budgets, and they're rarely needed once ESPN + a scraper are in place.

## Key Findings

1. **ESPN's hidden API has full UFC/MMA coverage**, structurally identical to its soccer endpoints. The base pattern `site.api.espn.com/apis/site/v2/sports/{sport}/{league}/...` works with `sport=mma`, `league=ufc`. No key, no auth, JSON, and it's the same backend ESPN.com/the app use.
2. ESPN exposes a **drill-down object model**: scoreboard → events → competitions (individual fights) → competitors (fighters) → statistics, plus standalone athlete profiles on `sports.core.api.espn.com`.
3. **Rankings are the notable hole** in ESPN's MMA API. There is no `mma/ufc/rankings` endpoint analogous to college football's `/rankings`; ESPN publishes MMA rankings only as editorial articles.
4. **Open datasets are excellent and free** for historical data — `ufcstats.com` scrapes (Greco1899/scrape_ufc_stats has pre-scraped CSVs auto-refreshed daily via GCP), jansen88/ufc-data (30 years of fights + odds), and the Octagon API (open-source rankings/fighters).
5. **Paid APIs** range from genuinely hobby-friendly (API-Sports MMA, The Odds API) to enterprise-only (Sportradar, SportsDataIO, OddsMatrix).
6. **Scraping difficulty varies sharply**: `ufcstats.com` is easy and scraper-friendly; UFC.com requires a US IP; **Tapology has strong anti-scraping defenses and will IP-block bulk scrapes**; Sherdog is scrapeable (one rvest-based study pulled 143,602 fighters and 484,061 fight entries from it).

## Details

### 1. FREE OPTIONS (Preferred)

#### A. ESPN Hidden API — the recommended primary source
**Base domains:** `site.api.espn.com` (summary/list feeds) and `sports.core.api.espn.com` (detailed relational feeds). No API key; plain HTTP GET; pure JSON.

**Confirmed UFC endpoints:**
- Scoreboard (upcoming + recent cards): `https://site.api.espn.com/apis/site/v2/sports/mma/ufc/scoreboard` (accepts `?dates=YYYYMMDD`)
- News: `https://site.api.espn.com/apis/site/v2/sports/mma/ufc/news`
- Event details: `http://sports.core.api.espn.com/v2/sports/mma/leagues/ufc/events/{eventId}`
- Fight (competition) details: `.../events/{eventId}/competitions/{competitionId}`
- Fight status/result: `.../events/{eventId}/competitions/{competitionId}/status`
- Competitor (fighter) per-fight stats: `.../events/{eventId}/competitions/{competitionId}/competitors/{athleteId}/statistics`
- Fighter profile: `http://sports.core.api.espn.com/v2/sports/mma/athletes/{athleteId}`
- Fighter record: `.../athletes/{athleteId}/records`; career stats `.../athletes/{athleteId}/statistics`; event log `.../athletes/{athleteId}/eventlog`

**What the scoreboard returns:** `leagues[]` (UFC, id 3321), a full `calendar[]` of the season's events, and `events[]`. Each event has `id`, `name` (e.g. "UFC Fight Night: Allen vs. Costa"), `date`, and `competitions[]` (the fight card). Each competition carries: `type` (weight class, e.g. Middleweight/Lightweight/W Strawweight), `venue` (fullName, city/state/country, indoor), `competitors[]` (each with `athlete.fullName`, `flag.alt` = nationality, `winner` bool, and `records[]` with W-L-D `summary` like "8-4-0"), `status` (scheduled/final, clock, period), `format.regulation.periods` (3 or 5 rounds), `cardSegment` (Main Card/Prelims), and `broadcast`.

**Results data (completed fights)** come from the competition `/status` endpoint: `result.displayName` = method ("KO/TKO", "Submission", "Decision"), `result.description` ("Punches"), `result.target` ("Head"); `period` = round; `displayClock` = time of finish (e.g. "4:21"). (All field names above were verified against live ESPN JSON on 2026-06-10.)

**Fighter profile fields:** fullName, nickname, weight/displayWeight, height/displayHeight, reach/displayReach, age, dateOfBirth, gender, `weightClass`, `stance` (Orthodox/Southpaw), `citizenship`/`flag` (nationality), `association` (gym), and `$ref` links to statistics and records. The record endpoint gives W-L-D plus `stats[]`: wins, losses, draws, noContests, submissions, tkos, titleWins, etc. Per-fight competitor statistics are very granular: knockDowns, sigStrikesLanded/Attempted, positional strike breakdowns, takedownsLanded/Attempted, takedownAccuracy, advances, reversals, submissions, timeInControl.

**Integration quirk:** internal `$ref` links in the JSON point to `sports.core.api.espn.pvt`; swap `.pvt` → `.com` to fetch them publicly. `/teams`-style lists cap at 50 rows (paginate with `&page=N`). No published rate limits — be respectful and cache.

**Caveats:** Unofficial and undocumented; ESPN can change/break endpoints without notice. **No rankings endpoint.**

#### B. Other unofficial/undocumented APIs
- **Octagon API** (victor-lillo/octagon-api) — open-source, free, MMA fighters + rankings + divisions, scraped from UFC.com, served as JSON (deployable to Cloudflare Workers). Best free source to fill ESPN's rankings gap.
- **Unofficial Tapology API** (RapidAPI, YannAries) — fighter profiles, records by method, weight class, nationality, upcoming/past events. Reads Tapology data; fair-use/attribution conditions apply.
- **mma-api** (BelNaruto, on RapidAPI) — wraps ESPN data: scoreboard, fighter profiles, rankings (cached 15 min), historical fights.

#### C. Open datasets (GitHub / Kaggle)
- **Greco1899/scrape_ufc_stats** — pre-scraped CSVs (events, fight details, fight results, fight stats, fighter details, tale-of-the-tape), auto-refreshed daily via a GCP Cloud Run job. Download ZIP, no code needed. Sourced from ufcstats.com.
- **jansen88/ufc-data** — combines ~30 years of match history (from 1994), fighter stats, and ~9 years of betting odds (from Nov 2014; from betmma.tips).
- **komaksym/UFC-DataLab** — every UFC fight + fighter stats + OCR-parsed official scorecards.
- **mtoto/ufc.stats** — tidy per-round dataframe, 37 variables (R package).
- Multiple Kaggle "every UFC fight 1993–2021/2023" datasets (good for seeding historical tables; static).
- **ufc-scraper (readthedocs)** — documented table schemas: events (name/date/location), fights (participants/outcome/weight class/method), fighters (physical + record), rounds (significant strikes, takedowns, control time).

#### D. Web scraping sources (difficulty assessment)
- **ufcstats.com** — *Easiest, scraper-friendly.* HTML tables; events → fights → round-by-round stats. Many mature Scrapy/BeautifulSoup repos exist. Best for historical/statistical depth. **Recommended scrape target if you self-scrape.**
- **UFC.com** — official; richest fighter bios + official rankings; **requires a USA IP**; slower to scrape (per-profile crawl).
- **Tapology** — *Hardest.* JavaScript pagination, unique structure, **strong anti-scraping/IP-blocking** — bulk scraping will get you blocked. Use the unofficial RapidAPI wrapper instead if you need Tapology data.
- **Sherdog** — large database (a published rvest study obtained 143,602 fighters and 484,061 fight entries); scrapeable with rvest/Scrapy; good for fight history.

### 2. PAID / FREEMIUM APIs

| Provider | Free tier | Paid entry | UFC data depth | Verdict for hobby |
|---|---|---|---|---|
| **API-Sports MMA** (`v1.mma.api-sports.io`) | Yes — 100 req/day, free forever (quota resets 00:00 UTC, unused lost) | From $10/mo; tiers Pro $19 / Ultra $29 / Mega $39 | Schedules, fighters, fights, results, odds; JSON/REST; key via `x-apisports-key` header | **Best paid option** if you outgrow ESPN; cheap, real free tier, good docs, multi-language code samples |
| **The Odds API** | Yes — 500 credits/month free (includes Historical Odds) | $30/mo (20k), $59 (100k), $119 (5M), $249 (15M) | **Odds only** — `mma_mixed_martial_arts` h2h + limited totals; historical from June 2020 | **Recommended for odds**; simple JSON, free tier fine for hobby |
| **RapidAPI MMA listings** (MMA Stats, MMA API, UFC Fighters, Unofficial Tapology) | Varies (often free trial / 50 req) | Varies per listing | Fighter profiles, rankings, events, historical fights (many wrap ESPN or Tapology) | Convenient if you want one key/billing; quality varies |
| **SportsDataIO MMA/UFC** | 30-day free trial (no card) | Enterprise quote | Full lifecycle: scores, stats, odds, projections, news, historical | Overkill/expensive for hobby; trial only good for evaluation |
| **Sportradar MMA v2** | Trial key (rolling 30-day quota, lower rate limit) | Enterprise contract — third-party comparisons cite "$10,000+/mo" up to the "$30K+/mo range"; sales-gated, no public price | All UFC events incl. Dana White's Contender Series; schedules, live results, champions-by-weight-class, competitor stats, season probabilities; XML/JSON | **Not for hobby** — pricing and B2B onboarding make it impractical |
| **OddsMatrix / OpticOdds / Genius** | No | Enterprise/bookmaker | Live odds + round-by-round, 350+ markets | Bookmaker-grade; not for hobby |
| **Zyla UFC Fighter Data API** | 7-day trial, 50 req | Subscription | Rankings (champion + 1–15 per division), fighter stats, upcoming bouts | Niche; rankings convenience |

Notes on Sportradar specifics: trial and production return the same real-world data (trial just has lower rate limits); API key passed as `?api_key=`; XML or JSON; date/time in ISO 8601 UTC; data cached ~1s–120s per `cache-control` header; "competition" = event, "season" = the single event's year — a quirk of mapping UFC onto their generic schema.

The Odds API credit math is worth understanding: a call costs `markets × regions` credits, and `GET /historical` costs 10× the standard rate — so the free 500 credits is roughly 50 historical calls when querying a single market/region, or far fewer if you query multiple regions/markets.

### 3. DATA-TYPE AVAILABILITY MATRIX

| Data type | ESPN hidden | ufcstats scrape | Octagon API | API-Sports MMA | The Odds API | Sportradar MMA |
|---|---|---|---|---|---|---|
| Upcoming events & fight cards | ✅ | ✅ (upcoming table) | ➖ (fighters/rankings) | ✅ | ✅ (odds events) | ✅ |
| Live scores / results | ✅ (status: method/round/time) | ❌ (post-event only) | ❌ | ✅ | ➖ (odds only) | ✅ |
| Fighter profiles (record, weight class, nationality, stance, reach) | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ (competitor) |
| Historical fight results (method/round/time) | ✅ (per event) | ✅ (deep, round-by-round) | ❌ | ✅ | ➖ (historical odds) | ✅ |
| Rankings | ❌ | ❌ (not on ufcstats) | ✅ | ➖ | ❌ | Champions feed (not full 1–15) |
| Odds | ➖ (some via core odds ref) | ❌ | ❌ | ✅ | ✅ | Add-on |

### 4. PRACTICAL INTEGRATION NOTES (Kotlin/JVM)

- **Formats:** ESPN, API-Sports, The Odds API → JSON/REST. Sportradar → XML or JSON.
- **Auth:** ESPN none; API-Sports `x-apisports-key` header; The Odds API `?apiKey=`; Sportradar key in query (`?api_key=`); SportsDataIO `Ocp-Apim-Subscription-Key` header or query param.
- **Rate limits:** ESPN unpublished (cache aggressively, expect possible blocks on heavy use); API-Sports free = 100 req/day (resets 00:00 UTC), with per-minute limits surfaced in `X-RateLimit-Limit`/`X-RateLimit-Remaining` and daily counts in `x-ratelimit-requests-limit`/`-remaining`; The Odds API 500 credits/mo (a call costs `markets×regions` credits); Sportradar rolling 30-day quota + QPS limit per product.
- **Kotlin/JVM example:** **alexvanyo/SportsFeed** is a Kotlin MVVM Android app that consumes ESPN's public API with no key — directly reusable as an architectural reference (Retrofit `EspnService` + `FeedRepository` + `FeedViewModel` + databinding). For a JVM backend, a Retrofit/OkHttp + kotlinx.serialization client pointed at `site.api.espn.com` mirrors your existing World Cup module.
- **Architecture recommendation:** Put ESPN behind your own gateway/service layer (as the World Cup module likely does) so endpoint changes require one config change, and cache responses (the `$ref` model means many calls per event).

## Recommendations

**Stage 1 — MVP (free, mirrors World Cup module):**
1. Build the UFC module against ESPN's hidden API: `mma/ufc/scoreboard` for the event list/fight cards, the core-API event/competition/status endpoints for results (method/round/time), and `athletes/{id}` + `/records` for fighter profiles. Reuse your World Cup Retrofit/serialization layer; only the response models differ.
2. Cache aggressively (events are weekly) and remember the `.pvt`→`.com` `$ref` swap.

**Stage 2 — Fill the rankings gap (free):**
3. Add the **Octagon API** (self-host the open-source scraper on a schedule) OR a small weekly UFC.com/ufcstats scrape for divisional rankings, since ESPN has none. Octagon is the lowest-effort path.

**Stage 3 — Historical depth (free):**
4. Seed your DB from **Greco1899/scrape_ufc_stats** CSVs (auto-refreshed daily) or **jansen88/ufc-data** for full fight history + stats. Re-sync periodically.

**Stage 4 — Optional enrichment:**
5. Add **The Odds API** (free 500 credits/mo) if you want betting odds.
6. Only if ESPN proves unreliable or you need an SLA, move the live/results feed to **API-Sports MMA** (free 100/day, $19/mo Pro) — the only paid option that makes sense at hobby scale.

**Thresholds that change the plan:**
- If ESPN endpoints break or rate-limit you in production → migrate live feed to API-Sports MMA.
- If you need guaranteed uptime/commercial SLA → evaluate the SportsDataIO trial, but expect enterprise pricing.
- If your app monetizes or scales beyond hobby traffic → revisit licensing (ESPN ToS, Tapology/Sherdog fair-use) before relying on scraped data commercially.

## Caveats
- **ESPN's API is unofficial.** No documentation, no SLA, endpoints can disappear without notice. Fine for a hobby module; wrap it so you can swap providers.
- **No ESPN rankings endpoint** — confirmed absent from both canonical community docs (the akeaswaran gist and pseudo-r/Public-ESPN-API); you must source rankings elsewhere. This was the single area verified most carefully; it remains "not documented/not found" rather than a proven hard 404.
- **Scraping legality/ToS:** ufcstats.com is permissive in practice; Tapology explicitly restricts republishing rankings without consent and actively blocks scrapers; UFC.com needs a US IP. Respect robots.txt and fair-use, especially if you ever monetize.
- **Sportradar/SportsDataIO pricing is opaque and sales-gated**; the dollar figures cited come from third-party comparison sites, not official rate cards, and may be outdated.
- **Betting odds** in historical datasets have gaps for older fights and name-matching issues; treat as approximate.
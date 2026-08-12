# NooNoo-Web: Technische Implementierungs-Anleitung (Accountsystem, Google Sheets, H2H & Termin-Sync)

## TL;DR
- **Accountsystem:** Ktor 3.5.x mit `Authentication` (form) + `Sessions` (verschlüsselte Cookie-Sessions via `SessionTransportTransformerEncrypt`), Passwort-Hashing mit **Argon2id** (OWASP-Minimum: m=19456 KiB / 19 MiB, t=2, p=1) über Bouncy Castle bzw. Spring Security Crypto `Argon2PasswordEncoder`, RateLimit-Plugin gegen Brute Force, generische Fehlermeldungen + Dummy-Hash gegen User-Enumeration/Timing. Der fehlende Passwort-Reset ist vertretbar, muss dem Nutzer aber prominent kommuniziert werden.
- **Google Sheets:** Nur `spreadsheets.readonly` – aber das ist ein **sensitiver Scope**, daher braucht die öffentliche App den Google-OAuth-Verification-Prozess. Ohne Verification bleibt die App im Testing-Modus mit 100-Nutzer-Cap und 7-Tage-Refresh-Token-Ablauf → für eine öffentliche Website nicht tragfähig. Der Ausweg mit dem geringsten Verification-Aufwand ist `drive.file` + Google Picker (non-sensitive) oder ein API-Key für öffentlich freigegebene Sheets.
- **H2H & Sync:** OpenLigaDB (kostenlos, deckt bl1 **und** bl2 ab) über `GET /getmatchdata/{teamId1}/{teamId2}` für Head-to-Head und `GET /getlastchangedate/...` für effiziente Änderungserkennung; täglicher Terminabgleich als Kotlin-Coroutine-Loop (pragmatisch) oder Quartz (persistent).

---

## Key Findings

1. **Argon2id ist 2026 der OWASP-Standard.** Das OWASP Password Storage Cheat Sheet fordert wörtlich: *„Use Argon2id with a minimum configuration of 19 MiB of memory, an iteration count of 2, and 1 degree of parallelism."* Alternativkonfiguration laut derselben Quelle: m=47104 KiB (46 MiB), t=1, p=1. bcrypt ist akzeptable Legacy-Alternative: *„For legacy systems using bcrypt, use a work factor of 10 or more and with a password limit of 72 bytes."*
2. **`spreadsheets.readonly` ist ein sensitiver Scope** – eine öffentliche App muss verifiziert werden. Das ist die zentrale Stolperfalle des gesamten Projekts.
3. **OpenLigaDB** ist die beste Wahl für 1./2. Bundesliga-H2H: kostenlos, kein Auth, dedizierter Team-vs-Team-Endpoint, dazu `getlastchangedate` und `lastUpdateDateTime` pro Match für effizientes Polling.
4. Für ein kleines Ktor-Projekt reicht ein **Coroutine-basierter Scheduler**; Quartz nur wenn Persistenz über Neustarts nötig ist.
5. Refresh-Tokens gehören **AES-256-GCM-verschlüsselt** in PostgreSQL, Schlüssel aus Umgebungsvariable/KMS.

---

## A) ACCOUNTSYSTEM IN KTOR

Relevante offizielle Doku: Ktor Authentication (`ktor.io/docs/server-auth.html`), Sessions (`ktor.io/docs/server-sessions.html`), Session-Auth (`ktor.io/docs/server-session-auth.html`), RateLimit (`ktor.io/docs/server-rate-limit.html`), OWASP Password Storage Cheat Sheet (`cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html`).

### A.1 Abhängigkeiten (Gradle Kotlin DSL)

```kotlin
dependencies {
    // Ktor Server (Version 3.5.2 zum Stand 08/2026)
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-sessions:$ktorVersion")
    implementation("io.ktor:ktor-server-rate-limit:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")

    // Passwort-Hashing – Variante A: Spring Security Crypto (kapselt Argon2 sauber)
    implementation("org.springframework.security:spring-security-crypto:6.3.1")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1") // wird von Argon2PasswordEncoder benötigt

    // Exposed + Flyway + Postgres
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("org.flywaydb:flyway-database-postgresql:10.17.0")
    implementation("org.postgresql:postgresql:42.7.3")
}
```

> Entscheidung Tim: Spring Security Crypto `Argon2PasswordEncoder` bringt Bouncy Castle als transitive Abhängigkeit und kapselt Salt-Erzeugung, Parameter-Encoding und konstante-Zeit-Vergleiche. Alternativ direkt `argon2-jvm` (JNI-Binding, `de.mkammerer:argon2-jvm`) – schneller, benötigt aber native libargon2. Für eine reine JVM-Server-Umgebung ohne native Abhängigkeiten ist Spring Security Crypto der robusteste Weg.

### A.2 Passwort-Hashing mit Argon2id

OWASP Password Storage Cheat Sheet (Stand 2026): **Argon2id mit Minimum 19 MiB Speicher (m=19456 KiB), Iterationen t=2, Parallelität p=1.** Alternative Konfiguration bei mehr Ressourcen: 46 MiB (m=47104) / t=1 / p=1. Schnelle Hashes (SHA-256 allein) sind ungeeignet, da GPU-brute-forcebar. bcrypt bleibt akzeptabel mit Work Factor ≥10 (72-Byte-Passwortlimit beachten).

```kotlin
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder

object PasswordHasher {
    // saltLength=16, hashLength=32, parallelism=1, memory=19456 KiB, iterations=2
    private val encoder = Argon2PasswordEncoder(16, 32, 1, 19456, 2)

    fun hash(raw: String): String = encoder.encode(raw)
    fun verify(raw: String, stored: String): Boolean = encoder.matches(raw, stored)
}
```

Der resultierende Hash ist selbstbeschreibend im PHC-String-Format (`$argon2id$v=19$m=19456,t=2,p=1$<salt>$<hash>`), Salt und Parameter sind eingebettet – man speichert nur diesen einen String.

**Wichtig:** Parameter am Produktionsserver einmessen. Ziel ist ein spürbarer, aber login-tauglicher Aufwand (in der Praxis ~250–500 ms; einige Quellen nennen bis 1 s). Wenn 19456 KiB unter Last zu viel RAM zieht (jeder gleichzeitige Login belegt 19 MiB), Login-Parallelität über das RateLimit-Plugin begrenzen.

### A.3 Datenbank-Schema (Flyway-Migration)

`src/main/resources/db/migration/V2__accounts.sql`:

```sql
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(32) NOT NULL,
    username_ci   VARCHAR(32) NOT NULL, -- lower(username) für Uniqueness/Lookup
    password_hash TEXT        NOT NULL, -- Argon2id PHC-String
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_username_ci UNIQUE (username_ci)
);

CREATE TABLE sessions (
    id         VARCHAR(64) PRIMARY KEY,        -- Session-ID (falls Server-Side-Storage)
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    data       TEXT
);
CREATE INDEX idx_sessions_user ON sessions(user_id);

CREATE TABLE oauth_tokens (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider       VARCHAR(32) NOT NULL DEFAULT 'google',
    refresh_token  BYTEA NOT NULL,            -- AES-256-GCM Ciphertext (IV||Cipher+Tag)
    access_token   BYTEA,                     -- optional, kurzlebig
    access_expires TIMESTAMPTZ,
    sheet_id       VARCHAR(128),              -- vom Nutzer hinterlegtes Sheet
    scopes         TEXT,
    key_version    INT NOT NULL DEFAULT 1,    -- für Schlüsselrotation
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_oauth_user_provider UNIQUE (user_id, provider)
);
```

Exposed-Tabellendefinition (Auszug):

```kotlin
object Users : LongIdTable("users") {
    val username     = varchar("username", 32)
    val usernameCi   = varchar("username_ci", 32).uniqueIndex()
    val passwordHash = text("password_hash")
    val createdAt    = timestamp("created_at")
}
```

> Hinweis zu Sessions: Für den Anfang genügen **client-seitige, verschlüsselte Cookie-Sessions** (unten). Eine `sessions`-Tabelle brauchst du nur, wenn du serverseitige Invalidierung/Logout-über-alle-Geräte willst. Beides ist kombinierbar (nur Session-ID im Cookie, Rest in DB).

### A.4 Sessions sicher konfigurieren

Ktor 3.0+ hat die Verschlüsselungsmethode aktualisiert; `SessionTransportTransformerEncrypt` verschlüsselt und signiert (Default-Authentifizierung HmacSHA256). Konfiguration:

```kotlin
@Serializable
data class UserSession(val userId: Long, val username: String)

install(Sessions) {
    val encryptKey = hex(System.getenv("SESSION_ENCRYPT_KEY")) // 16/32 Byte
    val signKey    = hex(System.getenv("SESSION_SIGN_KEY"))    // >= 32 Byte empfohlen
    cookie<UserSession>("NOONOO_SESSION") {
        cookie.path = "/"
        cookie.httpOnly = true                       // kein JS-Zugriff
        cookie.secure = true                         // nur über HTTPS
        cookie.extensions["SameSite"] = "Lax"        // CSRF-Schutz; "Strict" wenn kein Cross-Site-Redirect nötig
        cookie.maxAgeInSeconds = 60 * 60 * 24 * 30   // 30 Tage
        transform(SessionTransportTransformerEncrypt(encryptKey, signKey))
    }
}
```

Zusätzlich in Ktor 3.5.0 verfügbar: `sendOnlyIfModified = true`, damit der `Set-Cookie`-Header nur bei Änderung gesendet wird.

Session-Authentication-Provider:

```kotlin
install(Authentication) {
    session<UserSession>("auth-session") {
        validate { session -> session } // ggf. gegen DB prüfen
        challenge { call.respondRedirect("/login") }
    }
}
```

**Schlüsselerzeugung** (einmalig, sicher aufbewahren, z.B. `openssl rand -hex 32`). Bei Schlüsselrotation `backwardCompatibleRead` im Konstruktor von `SessionTransportTransformerEncrypt` nutzen.

### A.5 Registrierung & Login-Routen

```kotlin
routing {
    rateLimit(RateLimitName("auth")) {
        post("/register") {
            val params = call.receiveParameters()
            val username = params["username"]?.trim().orEmpty()
            val password = params["password"].orEmpty()
            // Validierung: Länge, erlaubte Zeichen, Passwort-Mindestlänge (NIST 800-63B: min. 8, besser 12+)
            if (!isValidUsername(username) || password.length < 12) {
                return@post call.respond(HttpStatusCode.BadRequest, "Registrierung fehlgeschlagen.")
            }
            val created = userService.tryCreateUser(username, PasswordHasher.hash(password))
            // Gegen Enumeration: KEINE Unterscheidung "Name vergeben" vs. Erfolg im Text
            if (created == null) {
                return@post call.respond(HttpStatusCode.Conflict, "Registrierung fehlgeschlagen. Bitte anderen Namen wählen.")
            }
            call.sessions.set(UserSession(created.id, created.username))
            call.respondRedirect("/")
        }

        post("/login") {
            val params = call.receiveParameters()
            val username = params["username"]?.trim().orEmpty()
            val password = params["password"].orEmpty()
            val user = userService.findByUsername(username)
            // Timing-Angriff vermeiden: IMMER hashen, auch wenn User nicht existiert
            val ok = if (user != null) PasswordHasher.verify(password, user.passwordHash)
                     else { PasswordHasher.verify(password, DUMMY_HASH); false }
            if (!ok) return@post call.respond(HttpStatusCode.Unauthorized, "Benutzername oder Passwort ist ungültig.")
            call.sessions.set(UserSession(user!!.id, user.username))
            call.respondRedirect("/")
        }
    }
}
```

`DUMMY_HASH` ist ein einmal vorab erzeugter Argon2id-Hash eines Zufallswerts. Er sorgt dafür, dass die Antwortzeit bei nicht existierendem User etwa gleich lang ist (Schutz gegen Timing-basierte User-Enumeration).

### A.6 Rate Limiting (Brute Force)

Ktor RateLimit-Plugin (Token-Bucket: `limit` = Bucket-Kapazität, `refillPeriod` = Nachfüllperiode):

```kotlin
install(RateLimit) {
    register(RateLimitName("auth")) {
        rateLimiter(limit = 10, refillPeriod = 60.seconds)
        requestKey { call -> call.request.origin.remoteHost } // pro IP
    }
}
```

`StatusPages` fängt 429 ab:

```kotlin
install(StatusPages) {
    status(HttpStatusCode.TooManyRequests) { call, _ ->
        call.respondText("Zu viele Versuche. Bitte später erneut probieren.", status = HttpStatusCode.TooManyRequests)
    }
}
```

Hinweis: Der IP-basierte Key ist hinter Reverse-Proxy nur zuverlässig, wenn `X-Forwarded-For` korrekt ausgewertet wird (Ktor `ForwardedHeaders`/`XForwardedHeaders`-Plugin installieren). Für stärkeren Schutz zusätzlich einen pro-Benutzername-Zähler in der DB oder ein Login-Fehlversuchs-Backoff. Alternativ existiert das Community-Plugin `dev.forst:ktor-rate-limiting` mit flexiblerer Key-Extraktion.

### A.7 Schutz gegen User-Enumeration

- **Login:** Immer dieselbe generische Meldung „Benutzername oder Passwort ist ungültig" – nie „Benutzer existiert nicht" vs. „Passwort falsch" (OWASP: *„An application should respond (both HTTP and HTML) in a generic manner."*).
- **Registrierung:** Hier ist Enumeration prinzipbedingt schwerer zu vermeiden, weil Namen eindeutig sein müssen. Bei NooNoo (Benutzername statt E-Mail, keine Bestätigung) ist die pragmatische Wahl: generische Meldung „Bitte anderen Namen wählen" + Rate Limiting + optional CAPTCHA. Da kein E-Mail-basiertes Verfahren existiert, ist der interstitielle „Wir haben dir eine E-Mail geschickt"-Trick nicht anwendbar. Akzeptiere, dass Namensverfügbarkeit erkennbar ist – das ist bei öffentlichen Benutzernamen (die man ohnehin sieht) ein deutlich geringeres Risiko als bei E-Mail-Adressen.
- **Timing:** Dummy-Hash wie oben.
- **CAPTCHA:** Bei öffentlichem Registrierungsformular gegen Bots empfehlenswert (z.B. hCaptcha/Cloudflare Turnstile). CAPTCHA einheitlich anzeigen, unabhängig davon ob der Name existiert.

### A.8 Sonderfall „Kein Passwort-Reset" – Risiken & Kommunikation

Das ist eine bewusste Vereinfachung, kein Sicherheitsfeature. Bewertung:

- **Risiko 1 – Datenverlust:** Vergisst der Nutzer Passwort *und* Benutzername, ist der Account (inkl. verbundenem Google-Sheet-Link und „Eigene Termine") unwiederbringlich verloren. Bei NooNoo ist das vertretbar, weil keine kritischen Daten gespeichert werden (nur Kalender-Präferenzen und eine Sheet-Referenz).
- **Risiko 2 – Account-Müll:** Verwaiste Accounts sammeln sich an. Empfehlung: Inaktive Accounts nach X Monaten automatisch löschen (DSGVO-freundlich).
- **Was dem Nutzer bei der Registrierung klar kommuniziert werden muss:**
  1. „Es gibt **keine** Passwort-Wiederherstellung. Notiere dir Benutzername und Passwort sicher (Passwort-Manager)."
  2. „Bei Verlust musst du dich **neu registrieren**; verbundene Google-Sheets und Einstellungen gehen verloren."
  3. Checkbox „Ich habe verstanden, dass es keine Wiederherstellung gibt" vor dem Absenden.
- **Sicherheitsvorteil:** Kein Reset-Flow = keine E-Mail-basierte Angriffsfläche (Account-Takeover über Reset-Links, E-Mail-Enumeration). Das ist tatsächlich ein positiver Nebeneffekt.
- **Empfehlung:** Trotzdem einen **freiwilligen** Recovery-Code bei Registrierung anbieten (einmalig generierter Code, den der Nutzer speichert und mit dem er ein neues Passwort setzen kann) – so bleibt „kein E-Mail-Reset" erhalten, ohne totalen Datenverlust bei reinem Passwort-Vergessen. Optional, Tims Entscheidung.

---

## B) GOOGLE SHEETS API ANBINDUNG

### B.0 Die zentrale Stolperfalle zuerst: OAuth Verification

**Das ist der wichtigste Abschnitt des ganzen Dokuments.** NooNoo ist eine öffentliche Website mit externen Nutzern (kein Google-Workspace-intern). Konsequenzen:

- Der benötigte Scope `https://www.googleapis.com/auth/spreadsheets.readonly` ist laut Google als **sensitiv** klassifiziert (nicht „restricted", aber auch nicht „non-sensitive").
- **Solange die App im „Testing"-Modus ist:** max. 100 Testnutzer (harte Grenze, laut Google-Cloud-Console-Hilfe: *„Projects configured with a publishing status of Testing are limited to up to 100 test users"*), jeder Nutzer sieht den „unverified app"-Warnbildschirm, und **Autorisierungen (inkl. Refresh-Token) laufen nach genau 7 Tagen ab** (Google wörtlich: *„Authorizations by a test user will expire seven days from the time of consent."*) → danach `invalid_grant`, Nutzer muss neu zustimmen. Für eine öffentliche App völlig untauglich.
- **Für Produktion:** App im OAuth-Consent-Screen auf „In Production" veröffentlichen. Da ein sensitiver Scope angefragt wird, ist der **Google-OAuth-Verification-Prozess** nötig: Nachweis der Domain-Inhaberschaft, Datenschutzerklärung, ausführliche Scope-Begründung und ein **Demo-Video** des OAuth-Flows. Google nennt für den eigentlichen Review „up to 10 days" nach vollständiger Einreichung; reale Erfahrungsberichte sprechen jedoch von **4–6 Wochen** end-to-end (erste Rückmeldung des Trust-&-Safety-Teams typischerweise nach 3–5 Tagen). Früh einreichen!
- **Security Assessment (CASA):** Ein vollständiges Drittanbieter-Security-Assessment (CASA Tier 2) ist bei **restricted** Scopes (z.B. `drive`, `drive.readonly`) Pflicht. `spreadsheets.readonly` ist „nur" sensitiv → **kein CASA-Assessment** nötig, aber der reguläre Verification-Prozess schon. Das ist ein wichtiger Grund, `drive`/`drive.readonly` unbedingt zu vermeiden.

**Handlungsoptionen für Tim (Entscheidung nötig):**
1. **Verification durchziehen** (für echten öffentlichen Betrieb): früh starten, da langwierig. Braucht öffentliche Datenschutzerklärung + Homepage.
2. **Feature auf 100 Beta-Tester begrenzen** (Testing-Modus) – aber wegen 7-Tage-Token-Ablauf müssten Nutzer wöchentlich neu verbinden. Nur für private Beta tragbar.
3. **`drive.file` + Google Picker (empfohlen):** Der Nutzer wählt genau *ein* Sheet über den Google-Picker-Dialog aus, die App bekommt nur auf diese eine Datei Zugriff. `drive.file` ist **non-sensitive** (von Google sogar „Recommended") → erspart potenziell den aufwändigen Verification-Prozess. Mehr Frontend-Aufwand (Picker-Einbindung).
4. **API-Key ohne OAuth:** Nutzer gibt sein Sheet „für alle mit dem Link sichtbar" frei, NooNoo liest es über einen API-Key. Keine OAuth-Verification. Trade-off: Sheet muss öffentlich lesbar sein (Datenschutz). **Für NooNoos Use Case oft die pragmatischste Lösung** – siehe B.8.

### B.1 Google Cloud Projekt einrichten (Schritt für Schritt)

1. **Projekt anlegen:** Google Cloud Console → oben Projektauswahl → „Neues Projekt" → Name „noonoo-web".
2. **Sheets API aktivieren:** „APIs & Dienste" → „Bibliothek" → „Google Sheets API" suchen → „Aktivieren". (Drive API NICHT aktivieren, wenn nicht nötig.)
3. **OAuth-Consent-Screen konfigurieren:** „APIs & Dienste" → „OAuth-Zustimmungsbildschirm" → User Type **External** → App-Name, Support-E-Mail, Logo, App-Domain, Links zu Datenschutzerklärung & Nutzungsbedingungen, autorisierte Domains.
4. **Scopes deklarieren:** Nur `.../auth/spreadsheets.readonly` (bzw. `drive.file`) hinzufügen (Prinzip der minimalen Rechte – nachträgliches Hinzufügen eines restricted Scopes startet die gesamte Verification neu).
5. **OAuth-Client-ID erstellen:** „Anmeldedaten" → „Anmeldedaten erstellen" → „OAuth-Client-ID" → Anwendungstyp **Web application**.
   - **Authorized JavaScript origins:** `https://noonoo.example` (bzw. `http://localhost:8080` für lokale Entwicklung).
   - **Authorized redirect URIs:** `https://noonoo.example/oauth/google/callback` (in Ktor via `urlProvider`).
6. Client-ID und Client-Secret kopieren → als Umgebungsvariablen (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`) hinterlegen, **nie** im Code.
7. Für Testing-Phase: Testnutzer-E-Mails im „Audience"-Tab eintragen.

Offizielle Doku: `developers.google.com/identity/protocols/oauth2/production-readiness/sensitive-scope-verification`, Scopes-Übersicht `developers.google.com/workspace/sheets/api/scopes`.

### B.2 OAuth-Scopes: readonly vs. drive.file

| Scope | Zugriff | Klassifizierung | Verification |
|---|---|---|---|
| `spreadsheets.readonly` | Lesen **aller** Sheets des Nutzers | **Sensitiv** | Verification nötig |
| `drive.file` | Nur Dateien, die der Nutzer explizit über den Google Picker auswählt | **Non-sensitive** (von Google „Recommended") | Nur Basis-Verification |
| `drive` / `drive.readonly` | Alle Drive-Dateien | **Restricted** | Verification + CASA-Assessment |

**Empfehlung – am wenigsten invasiv:** `drive.file` **in Kombination mit dem Google Picker** ist der eleganteste Weg: Zugriff nur auf das eine ausgewählte Sheet, non-sensitive, minimaler Verification-Aufwand. Nachteil: Google Picker (JavaScript) muss eingebunden und die Datei-Auswahl mit dem Sheet-Lesen (Sheets API) verknüpft werden.

**Trade-off-Entscheidung für Tim:**
- Reiner Server-Flow mit manueller Sheet-ID-Eingabe → braucht `spreadsheets.readonly` (sensitiv, Verification).
- Picker-basierter Flow → `drive.file` (non-sensitive), aber mehr Frontend-Aufwand.

### B.3 OAuth2 Authorization Code Flow in Ktor

Ktors `oauth`-Provider unterstützt den Authorization Code Flow (Doku: `ktor.io/docs/server-oauth.html`). Für **Refresh-Tokens** sind `access_type=offline` und `prompt=consent` zwingend (sonst gibt Google kein Refresh-Token bzw. nur beim allerersten Consent):

```kotlin
val applicationHttpClient = HttpClient(CIO) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
}

install(Authentication) {
    oauth("auth-oauth-google") {
        urlProvider = { "https://noonoo.example/oauth/google/callback" }
        client = applicationHttpClient
        providerLookup = {
            OAuthServerSettings.OAuth2ServerSettings(
                name = "google",
                authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
                accessTokenUrl = "https://oauth2.googleapis.com/token",
                requestMethod = HttpMethod.Post,
                clientId = System.getenv("GOOGLE_CLIENT_ID"),
                clientSecret = System.getenv("GOOGLE_CLIENT_SECRET"),
                defaultScopes = listOf("https://www.googleapis.com/auth/spreadsheets.readonly"),
                extraAuthParameters = listOf(
                    "access_type" to "offline",
                    "prompt" to "consent"          // erzwingt Refresh-Token bei jedem Consent
                )
            )
        }
    }
}
```

Callback-Route – Refresh-Token extrahieren und **verschlüsselt** speichern:

```kotlin
routing {
    authenticate("auth-oauth-google") {
        get("/oauth/google/login") { /* Auto-Redirect zu Google */ }
        get("/oauth/google/callback") {
            val principal: OAuthAccessTokenResponse.OAuth2? = call.principal()
            val session = call.sessions.get<UserSession>()
            if (principal != null && session != null) {
                val refreshToken = principal.refreshToken   // nur mit prompt=consent zuverlässig gesetzt
                val accessToken  = principal.accessToken
                oauthTokenService.store(
                    userId = session.userId,
                    refreshToken = refreshToken,     // wird intern AES-GCM-verschlüsselt (siehe B.7)
                    accessToken = accessToken,
                    expiresInSec = principal.expiresIn
                )
            }
            call.respondRedirect("/eigene-termine/sheet-waehlen")
        }
    }
}
```

> Wichtig: `principal.refreshToken` ist nur befüllt, wenn Google eins schickt. Mit `prompt=consent` erhältst du bei jeder Autorisierung eins. Ohne `prompt=consent` bekommst du es nur beim allerersten Consent des Nutzers – ein häufiger Bug.

### B.4 Werte aus einem Sheet abrufen (Sheets API v4)

Endpoint: `spreadsheets.values.get`
`GET https://sheets.googleapis.com/v4/spreadsheets/{spreadsheetId}/values/{range}`

Parameter:
- **range:** A1-Notation, z.B. `Termine!A2:C` (alle Zeilen ab 2 in Spalten A–C).
- **valueRenderOption:** `FORMATTED_VALUE` (Standard, wie im Sheet angezeigt), `UNFORMATTED_VALUE` (Rohwert), `FORMULA`.
- **dateTimeRenderOption:** `SERIAL_NUMBER` (Standard – Datum als Zahl seit 30.12.1899) oder `FORMATTED_STRING` (String im Zahlenformat des Sheets, abhängig von Sheet-Locale). Wird ignoriert, wenn `valueRenderOption=FORMATTED_VALUE`.

Beispiel mit Ktor HttpClient (gültiger access_token vorausgesetzt):

```kotlin
suspend fun readSheet(spreadsheetId: String, accessToken: String): List<List<String>> {
    val range = "Termine!A2:C"
    val resp: SheetValues = httpClient.get(
        "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$range"
    ) {
        url.parameters.append("valueRenderOption", "FORMATTED_VALUE")
        url.parameters.append("dateTimeRenderOption", "FORMATTED_STRING")
        bearerAuth(accessToken)
    }.body()
    return resp.values ?: emptyList()
}

@Serializable data class SheetValues(val range: String? = null, val values: List<List<String>>? = null)
```

**Datumswerte – zwei Wege (offizielle Doku: `developers.google.com/sheets/api/reference/rest/v4/DateTimeRenderOption`):**
- **`SERIAL_NUMBER`:** Datum/Zeit als Double (Tage seit 30.12.1899, Nachkommastelle = Tagesbruchteil). Locale-unabhängig, präzise – aber du musst selbst umrechnen (B.5) und die Sheet-Zeitzone kennen.
- **`FORMATTED_STRING`:** String wie im Sheet dargestellt, abhängig von Sheet-Locale (deutsche Locale → z.B. `03.05.2026 18:30`). Einfacher zu lesen, aber lokalisiertes Format muss geparst werden.

**Empfehlung für NooNoo:** Nutzer sollen Datum/Zeit als **ISO-8601-Text** eintragen (`2026-05-03 18:30`, siehe Abschnitt C). Dann liefert `FORMATTED_VALUE` diesen Text 1:1 zurück und du parst deterministisch mit `java.time` – ohne Serial-Number-Mathematik und ohne Locale-Fallen.

### B.5 Google Sheets Serial Date → Kotlin (java.time)

Google-Serial (offiziell dokumentiert): Ganzzahl = Tage seit **30.12.1899**, Nachkommateil = Anteil eines Tages. Beispiel aus der Doku: 1.1.1900 12:00 Uhr = 2.5; 1.2.1900 15:00 Uhr = 33.625. Das Jahr 1900 wird korrekt als **Nicht**-Schaltjahr behandelt.

```kotlin
import java.time.*

// Google-Sheets-Epoche
private val SHEETS_EPOCH: LocalDate = LocalDate.of(1899, 12, 30)

fun serialToLocalDateTime(serial: Double): LocalDateTime {
    val days = serial.toLong()
    val fractionOfDay = serial - days
    val date = SHEETS_EPOCH.plusDays(days)
    val nanosInDay = (fractionOfDay * 86_400.0 * 1_000_000_000.0).toLong()
    return date.atStartOfDay().plusNanos(nanosInDay)
}

// Interpretation MIT Sheet-Zeitzone -> Instant/ZonedDateTime
fun serialToInstant(serial: Double, sheetZone: ZoneId = ZoneId.of("Europe/Berlin")): Instant =
    serialToLocalDateTime(serial).atZone(sheetZone).toInstant()
```

> Achtung: Der Serial-Wert trägt **keine** Zeitzoneninformation. Er ist „wall clock time" der Sheet-Zeitzone. Die Sheet-Zeitzone aus den Metadaten (`spreadsheets.get` → `properties.timeZone`) lesen oder per Konvention `Europe/Berlin` annehmen.

### B.6 Bibliothek: offizielle Java-Client-Library vs. Ktor HttpClient

| Kriterium | `google-api-services-sheets` (offiziell) | Ktor `HttpClient` direkt gegen REST |
|---|---|---|
| Auth/Token-Refresh | automatisch (google-auth-library) | selbst implementieren |
| Abhängigkeiten | schwer (google-api-client, http-client-jetty, transitiv viel) | leichtgewichtig, schon im Projekt |
| Coroutines | blockierend (muss in `Dispatchers.IO` gewrappt werden) | nativ suspend |
| Kontrolle/Transparenz | weniger | volle Kontrolle über Requests |
| Passt zur hexagonalen Architektur | mittel | gut (dünner Adapter im Port) |

**Empfehlung:** Für ein Ktor-Projekt mit hexagonaler Architektur und nur einem lesenden Endpoint (`values.get`) ist der **direkte Ktor-HttpClient-Weg** die bessere Wahl: keine schwere blockierende Bibliothek, native Coroutines, ein sauberer Adapter im Sheets-Port. Den Token-Refresh (POST an `https://oauth2.googleapis.com/token` mit `grant_type=refresh_token`) implementierst du in ~30 Zeilen selbst. Die offizielle Library lohnt nur, wenn viele verschiedene Sheets-Operationen (Schreiben, Formatierung, Batch) gebraucht werden.

Token-Refresh-Snippet:

```kotlin
@Serializable data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("token_type") val tokenType: String
)

suspend fun refreshAccessToken(refreshToken: String): TokenResponse =
    httpClient.submitForm(
        url = "https://oauth2.googleapis.com/token",
        formParameters = parameters {
            append("client_id", System.getenv("GOOGLE_CLIENT_ID"))
            append("client_secret", System.getenv("GOOGLE_CLIENT_SECRET"))
            append("refresh_token", refreshToken)
            append("grant_type", "refresh_token")
        }
    ).body()
```

### B.7 Refresh-Tokens sicher in PostgreSQL speichern (AES-256-GCM)

Refresh-Tokens sind langlebige Zugangsdaten – müssen **verschlüsselt** gespeichert werden (nicht gehasht, da der Klartext wieder gebraucht wird). Empfohlen: **AES-256-GCM** (authenticated encryption: Vertraulichkeit + Integrität in einem; Manipulation am Ciphertext lässt die Entschlüsselung fehlschlagen). Schlüssel aus Umgebungsvariable (bzw. Cloud KMS via Envelope Encryption in größeren Setups).

```kotlin
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

object TokenCrypto {
    private const val IV_LEN = 12          // 96-bit Nonce für GCM
    private const val TAG_BITS = 128
    private val key = SecretKeySpec(
        java.util.Base64.getDecoder().decode(System.getenv("TOKEN_ENC_KEY")), "AES"
    ) // 32 Byte = AES-256
    private val rng = SecureRandom()

    fun encrypt(plain: String): ByteArray {
        val iv = ByteArray(IV_LEN).also { rng.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return iv + ct   // IV voranstellen (IV || Ciphertext+Tag)
    }

    fun decrypt(blob: ByteArray): String {
        val iv = blob.copyOfRange(0, IV_LEN)
        val ct = blob.copyOfRange(IV_LEN, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }
}
```

Best Practices: eindeutige zufällige IV pro Verschlüsselung (nie wiederverwenden!), Schlüssel nie im Code/Repo, Schlüsselrotation vorsehen (`key_version`-Spalte im Schema). In produktiven Cloud-Setups Envelope Encryption: Daten-Key (DEK) lokal für AES-GCM, mit KMS-Master-Key „gewrappt" und der gewrappte DEK neben dem Ciphertext gespeichert.

### B.8 Rate Limits, Quotas & Polling-Strategie

Google Sheets API Quotas (offiziell, `developers.google.com/workspace/sheets/api/limits`): **300 Leseanfragen/Minute pro Projekt** und **60 Leseanfragen/Minute pro Nutzer pro Projekt**; pro Tag unbegrenzt, solange das Minutenlimit eingehalten wird. Überschreitung → HTTP **429 (Too Many Requests)**. Google wörtlich: *„When Sheets processes a request for more than 180 seconds, the request returns a timeout error."*

Konsequenzen für NooNoo:
- Bei vielen Nutzern ist das **300/min-Projektlimit** der Flaschenhals. Sheets **nicht** bei jedem Seitenaufruf live abfragen.
- **Caching:** „Eigene Termine" serverseitig cachen (z.B. 10–15 min TTL pro Nutzer), nur periodisch oder auf expliziten „Aktualisieren"-Klick neu laden.
- **Polling-Intervall:** Ein Hintergrund-Sync alle 10–15 min pro verbundenem Sheet ist mehr als ausreichend für Kalenderdaten. Bei N Nutzern: N Requests/Intervall – bei 300/min bleibt viel Luft.
- **Exponentielles Backoff bei 429:** 1 s, 2 s, 4 s, 8 s … (+ Jitter), bis Max (~32–64 s). Von Google explizit empfohlen (*„truncated exponential backoff"*).

```kotlin
suspend fun <T> withBackoff(maxRetries: Int = 5, block: suspend () -> T): T {
    var attempt = 0
    while (true) {
        try { return block() }
        catch (e: TooManyRequestsException) {
            if (attempt++ >= maxRetries) throw e
            val delayMs = (1000L shl (attempt - 1)) + Random.nextLong(0, 250)
            delay(minOf(delayMs, 32_000L))
        }
    }
}
```

**API-Key-Alternative (siehe B.0 Option 4):** Für ein öffentlich freigegebenes Sheet (Link-Sharing „jeder mit Link") reicht ein API-Key statt OAuth: `GET .../values/{range}?key=API_KEY`. Kein Refresh-Token, keine OAuth-Verification. Trade-off: Sheet muss öffentlich lesbar sein.

---

## C) ANLEITUNG FÜR DIE TABELLENEINRICHTUNG (für Endnutzer)

*(Dieser Abschnitt ist bewusst laienverständlich formuliert und kann 1:1 in die App-Hilfe übernommen werden.)*

### So richtest du dein Google-Tabellenblatt für „Eigene Termine" ein

**1. Spalten und Überschriften (Zeile 1)**

Lege in deinem Google-Tabellenblatt genau drei Spalten an:

| Zelle | Inhalt (Überschrift in Zeile 1) |
|---|---|
| **A1** | `Von` |
| **B1** | `Bis` |
| **C1** | `Titel` |

Ab **Zeile 2** trägst du deine Termine ein – eine Zeile pro Termin. Es dürfen keine weiteren Spalten mit Daten befüllt sein.

**2. Format für Datum und Uhrzeit – bitte genau so**

Trage Datum und Uhrzeit als **Text im Format `JJJJ-MM-TT SS:MM`** (ISO 8601) ein, z.B. `2026-05-03 18:30`.

- **Warum dieses Format?** Es ist eindeutig und unabhängig von Länder-/Spracheinstellungen. Das deutsche Format `TT.MM.JJJJ` (z.B. `03.05.2026`) führt oft zu Verwechslungen, weil Google Sheets je nach Locale Tag und Monat vertauscht (aus `03.05.` wird schnell der 5. März statt 3. Mai). ISO 8601 sortiert außerdem automatisch korrekt und wird von der App zuverlässig erkannt.
- **Tipp:** Damit Google die Eingabe nicht automatisch in ein anderes Datumsformat umwandelt, kannst du die Zellen vorab auf „Nur Text" formatieren (Format → Zahl → Nur Text) oder ein führendes Apostroph setzen (`'2026-05-03 18:30`).

**3. Zeitzone einstellen (wichtig!)**

Stelle die Zeitzone deines Tabellenblatts auf **(GMT+01:00) Berlin** ein:
Datei → Einstellungen → Tab „Allgemein" → Zeitzone → „Berlin".
So werden alle Uhrzeiten als deutsche Ortszeit (inkl. automatischer Sommer-/Winterzeit) interpretiert. Wenn du die Zeitzone nicht einstellst, kann es zu Verschiebungen um 1–2 Stunden kommen.

**4. Die „Bis"-Spalte darf leer bleiben**

- Ist nur **Von** ausgefüllt (Bis leer), dauert der Termin automatisch **1 Stunde**.
- Ist auch **Bis** ausgefüllt, gilt der eingetragene Zeitraum.

**5. Was passiert bei Fehlern / leeren Zellen?**

- **Leere Zeile:** wird übersprungen.
- **Von fehlt, aber Titel/Bis vorhanden:** Zeile wird übersprungen (ohne Startzeit kein Termin).
- **Titel fehlt:** Termin wird mit „(ohne Titel)" angezeigt.
- **Unlesbares Datum** (z.B. Tippfehler `2026-13-40`): Zeile wird übersprungen und in einem Fehlerprotokoll vermerkt, das du in der App unter „Eigene Termine → Sync-Status" siehst.

**6. Beispieltabelle**

| Von | Bis | Titel |
|---|---|---|
| 2026-05-03 18:30 | 2026-05-03 20:00 | Grillabend mit Freunden |
| 2026-05-05 09:00 |  | Zahnarzttermin |
| 2026-05-10 14:00 | 2026-05-10 17:30 | Wandern im Harz |
| 2026-05-12 20:15 |  | Kino |

(Zeile 2 mit Bis = 1,5 h Termin; Zeile 3 ohne Bis = 1 h Standard.)

### Validierungs-Empfehlung für die App (für Tim)

**Grundsatz: Fehlerhafte Zeile überspringen + protokollieren, statt den ganzen Sync abzubrechen.** Begründung: Ein einzelner Tippfehler des Nutzers darf nicht dazu führen, dass gar keine Termine erscheinen. Die App sollte:
- Jede Zeile einzeln validieren (Datum parsebar? Von < Bis?).
- Gültige Zeilen importieren, ungültige mit Zeilennummer + Grund in ein pro-Nutzer-Sync-Log schreiben.
- Dem Nutzer eine sichtbare, aber nicht blockierende Rückmeldung geben („3 von 4 Terminen importiert, Zeile 3 hat ein ungültiges Datum").
- Robustes Parsing: primär strikt ISO 8601, optional Fallback auf `dd.MM.yyyy HH:mm` mit deutscher Locale.

```kotlin
private val ISO_SPACE = DateTimeFormatter.ofPattern("yyyy-MM-dd[ HH:mm]")
private val DE_FALLBACK = DateTimeFormatter.ofPattern("dd.MM.yyyy[ HH:mm]")

fun parseFlexible(raw: String): LocalDateTime? = runCatching {
    val s = raw.trim().replace('T', ' ')
    runCatching { LocalDateTime.parse(s, ISO_SPACE) }
        .getOrElse {
            // nur Datum -> Mitternacht
            runCatching { LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay() }
                .getOrElse { LocalDateTime.parse(s, DE_FALLBACK) }
        }
}.getOrNull()
```

---

## D) HEAD-TO-HEAD DATEN & TERMIN-SYNC

### D.1 API-Vergleich für 1./2. Bundesliga-H2H

| API | 2. Bundesliga? | H2H | Free Tier | Auth |
|---|---|---|---|---|
| **OpenLigaDB** | **Ja** (bl1 **und** bl2) | **Ja**, dedizierter Team-vs-Team-Endpoint | Komplett kostenlos, unbegrenzt | Keine |
| **API-Football** (api-sports.io) | Ja | Ja (`/fixtures/headtohead`) | **100 Requests/Tag** ($0/Monat) | API-Key |
| **football-data.org** | **Nein** (Free Tier = 12 Wettbewerbe, nur 1. Bundesliga „BL1", **nicht** die 2. Liga) | H2H im `/matches/{id}` | **10 Req/min**, 12 Wettbewerbe | API-Key |

**Klare Empfehlung: OpenLigaDB.** Gründe: kostenlos, keine Auth, deutschsprachig, deckt **beide** Ligen ab. Die 2. Bundesliga ist bei football-data.org im Free Tier nicht enthalten (dessen 12 Gratis-Wettbewerbe sind Premier League, La Liga, Bundesliga, Serie A, Ligue 1, Champions League, Eredivisie, Primeira Liga, Championship, Brasil. Série A, WM, EM), und API-Football limitiert auf 100 Requests/Tag. OpenLigaDB hat zudem einen echten Head-to-Head-Endpoint und einen Change-Detection-Mechanismus.

### D.2 OpenLigaDB Head-to-Head – konkrete Umsetzung

**Endpoint:** `GET https://api.openligadb.de/getmatchdata/{teamId1}/{teamId2}`
Beispiel: `https://api.openligadb.de/getmatchdata/40/7` (Bayern München vs. Dortmund). Swagger-Doku: `https://api.openligadb.de/`.

Der Endpoint liefert (live verifiziert) **alle** jemals erfassten Begegnungen der beiden Teams über **alle Wettbewerbe hinweg** (Bundesliga aller Saisons, DFB-Pokal, Champions League etc.), chronologisch aufsteigend (älteste zuerst).

**Wichtige JSON-Felder pro Match-Objekt (exakte Schreibweise beachten!):**
- `matchID` (Integer, großes „ID")
- `matchDateTime` (lokale Zeit, z.B. `"2016-11-19T18:30:00"`)
- `matchDateTimeUTC` (UTC mit Z)
- `leagueName`, `leagueShortcut` (z.B. `"bl1"`), `leagueSeason`, `leagueId`
- `team1`, `team2` (jeweils mit `teamId`, `teamName`, `shortName`, `teamIconUrl`)
- `matchIsFinished` (Boolean — **nicht** „matchIdFinished"; das ist ein Fehler in der alten Prosa-Doku, das reale JSON-Feld heißt `matchIsFinished`)
- `lastUpdateDateTime` (z.B. `"2016-11-24T19:57:28.54"`)
- `matchResults` (Array): je Element `resultName`, `pointsTeam1`, `pointsTeam2`, `resultOrderID`, `resultTypeID` (**1** = Halbzeit, **2** = Endergebnis nach 90 min, 4 = nach Verlängerung), `resultTypeKind` (`"HalfTime"`/`"After90Minutes"`/`"AfterExtraTime"`)

**Für die letzten 5 Begegnungen (Datum, Ergebnis, Wettbewerb):**
1. Response abrufen, nach `matchDateTime` **absteigend** sortieren.
2. Optional nur offizielle Wettbewerbe: nach `leagueShortcut` in `{bl1, bl2, dfb, cl, el}` filtern (der Endpoint liefert auch von Nutzern angelegte **inoffizielle** Test-Ligen → sonst verfälscht).
3. Nur `matchIsFinished == true` nehmen.
4. Die ersten 5 auswählen.
5. Endergebnis: aus `matchResults` das Element mit `resultTypeID == 2` (Fallback: höchster `resultOrderID`), dann `pointsTeam1:pointsTeam2`.
6. Wettbewerb: `leagueName` bzw. Mapping von `leagueShortcut`.

```kotlin
data class H2HEntry(val date: LocalDateTime, val home: String, val away: String,
                    val score: String, val competition: String)

fun mapLast5(matches: List<OlbMatch>): List<H2HEntry> =
    matches.filter { it.matchIsFinished && it.leagueShortcut in OFFICIAL }
        .sortedByDescending { it.matchDateTime }
        .take(5)
        .map { m ->
            val r = m.matchResults.firstOrNull { it.resultTypeID == 2 }
                ?: m.matchResults.maxByOrNull { it.resultOrderID }
            H2HEntry(
                date = LocalDateTime.parse(m.matchDateTime),
                home = m.team1.teamName, away = m.team2.teamName,
                score = if (r != null) "${r.pointsTeam1}:${r.pointsTeam2}" else "-",
                competition = m.leagueName
            )
        }
```

> Team-IDs: Über `GET /getavailableteams/bl1/2025` (bzw. bl2) die `teamId`-Werte der Saison holen und in eurer DB den eigenen Team-Datensätzen zuordnen. Diese Zuordnung einmal pflegen. Weitere nützliche Endpoints: `GET /getmatchdata/{shortcut}/{season}` (ganze Saison), `GET /getmatchdata/{shortcut}/{season}/{groupOrderId}` (ein Spieltag), `GET /getnextmatchbyleagueteam/{leagueId}/{teamId}` (nächstes Spiel; erster Parameter ist die numerische leagueId, **nicht** der Shortcut).

### D.3 Alternative/Ergänzung: H2H aus eigenen PostgreSQL-Daten

Da NooNoo ohnehin Spiele speichert, kann H2H auch komplett offline aus der eigenen DB berechnet werden – unabhängig von externer API-Verfügbarkeit. Voraussetzung: historische Ergebnisse sind gespeichert.

```sql
-- Letzte 5 Begegnungen zweier Teams (beide Heim/Auswärts-Konstellationen)
SELECT m.match_date, m.home_team_id, m.away_team_id,
       m.home_goals, m.away_goals, m.competition
FROM matches m
WHERE ((m.home_team_id = :teamA AND m.away_team_id = :teamB)
    OR (m.home_team_id = :teamB AND m.away_team_id = :teamA))
  AND m.is_finished = true
ORDER BY m.match_date DESC
LIMIT 5;
```

**Empfohlene Kombi:** OpenLigaDB als Quelle für den initialen Import + laufende Aktualisierung historischer Ergebnisse in die eigene DB; H2H-Anzeige dann aus der eigenen DB (schnell, offline-fähig, kein Rate-Limit). OpenLigaDB nur beim Sync-Job anfragen, nicht bei jedem Seitenaufruf.

### D.4 Terminänderungen effizient erkennen

OpenLigaDB bietet einen dedizierten Change-Detection-Mechanismus – genau für den in Änderungsplan Punkt 2 beschriebenen Use Case (vorläufige 11:00-Uhr-Platzhalter später final terminieren):

- **`GET /getlastchangedate/{leagueShortcut}/{leagueSeason}/{groupOrderId}`** – liefert einen ISO-Zeitstempel-String (live verifiziert, z.B. `"2022-10-02T19:27:31.71"`) der letzten Änderung an diesem Spieltag. Beispiel: `https://api.openligadb.de/getlastchangedate/bl1/2022/8`. Laut Doku explizit dafür gedacht, „unnötiges Pollen zu vermeiden".
- Zusätzlich hat **jedes Match-Objekt** ein Feld **`lastUpdateDateTime`**, mit dem man einzelne Spiele feingranular vergleichen kann.

**Effizienter Sync-Algorithmus:**
1. Pro relevanten Spieltag den gespeicherten `lastChangeDate` mit dem aktuellen `getlastchangedate`-Wert vergleichen.
2. Nur bei Abweichung den vollen Spieltag (`/getmatchdata/{shortcut}/{season}/{groupOrderId}`) laden.
3. Für jedes noch nicht gestartete Spiel (`matchIsFinished == false` und `matchDateTime` in Zukunft) `matchDateTime` mit dem gespeicherten Wert abgleichen und bei Änderung Datum/Uhrzeit aktualisieren.
4. Den neuen `lastChangeDate` speichern.

Das reduziert die Anzahl der Voll-Ladevorgänge drastisch.

### D.5 Scheduled Jobs in Kotlin/Ktor

| Ansatz | Persistenz über Neustart | Komplexität | Empfehlung |
|---|---|---|---|
| **Coroutine-Loop** (`launch` + `delay`) | Nein (in-process) | Sehr gering | **Für NooNoo empfohlen** |
| `ScheduledExecutorService` | Nein | Gering | Solide JVM-Alternative |
| **Quartz** | Ja (DB-Job-Store) | Mittel/hoch | Nur bei Cluster/Persistenzbedarf |
| JobRunr / kjob | Ja | Mittel | Bei Job-Queue-Bedarf |

**Empfehlung für ein kleines Ktor-Projekt:** Ein Coroutine-basierter Loop, an den Application-Lifecycle gekoppelt. Der tägliche Terminabgleich ist idempotent und unkritisch – geht ein Lauf verloren (Neustart), läuft der nächste ohnehin. Damit braucht es kein Quartz. (Bei Bedarf an Persistenz über Neustarts: Marco Gomieros Anleitung „Quartz on Ktor" oder das coroutine-basierte `kjob`.)

```kotlin
fun Application.scheduleMatchSync(syncService: MatchSyncService) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    monitor.subscribe(ApplicationStarted) {
        scope.launch {
            while (isActive) {
                try {
                    syncService.syncUpcomingMatches()   // getlastchangedate-Vergleich + Update
                } catch (e: Exception) {
                    log.error("Match-Sync fehlgeschlagen", e)
                }
                delay(delayUntilNext(4).inWholeMilliseconds)   // täglich 04:00 Europe/Berlin
            }
        }
    }
    monitor.subscribe(ApplicationStopping) { scope.cancel() }
}
```

Delay bis zur nächsten festen Uhrzeit (z.B. 04:00) berechnen:

```kotlin
fun delayUntilNext(hour: Int): Duration {
    val now = ZonedDateTime.now(ZoneId.of("Europe/Berlin"))
    var next = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
    if (!next.isAfter(now)) next = next.plusDays(1)
    return java.time.Duration.between(now, next).toKotlinDuration()
}
```

> Wenn NooNoo künftig in mehreren Instanzen/Containern läuft (horizontale Skalierung), würde der Coroutine-Loop in jeder Instanz laufen (Mehrfach-Sync). Dann entweder auf Quartz mit DB-Job-Store wechseln oder ein einfaches DB-Lock (`SELECT ... FOR UPDATE` auf eine `job_locks`-Zeile) setzen. Für Single-Instance-Betrieb ist der Loop optimal.

---

## Recommendations

**Stufe 1 – sofort (Accountsystem, keine externen Abhängigkeiten):**
1. Flyway-Migration `V2__accounts.sql` + Exposed-Tabellen anlegen.
2. Argon2id (Spring Security Crypto, m=19456/t=2/p=1) + verschlüsselte Cookie-Sessions (httpOnly/secure/SameSite=Lax) + RateLimit-Plugin implementieren.
3. Generische Fehlermeldungen + Dummy-Hash gegen Enumeration/Timing.
4. Registrierungs-Checkbox „kein Passwort-Reset" + optionalen Recovery-Code erwägen.

**Stufe 2 – parallel starten wegen langer Laufzeit (Google OAuth):**
5. **Zuerst entscheiden:** `spreadsheets.readonly` (Verification nötig) vs. `drive.file`+Picker (non-sensitive) vs. API-Key für öffentliche Sheets. **Empfehlung: `drive.file`+Picker**, um den Verification-Aufwand zu minimieren; wenn das zu viel Frontend ist, API-Key-Variante mit öffentlich freigegebenem Sheet.
6. Google-Cloud-Projekt + Consent-Screen + Datenschutzerklärung sofort aufsetzen und Verification **früh** einreichen (Google nennt „up to 10 days" Review, real eher 4–6 Wochen end-to-end).
7. OAuth-Flow in Ktor mit `access_type=offline` + `prompt=consent`; Refresh-Tokens AES-256-GCM-verschlüsselt speichern.
8. Sheet-Lesen über Ktor-HttpClient (nicht die schwere Java-Library); ISO-8601-Text-Konvention + serverseitiges Caching (10–15 min) + Backoff.

**Stufe 3 – Sportdaten & Sync:**
9. OpenLigaDB-Adapter: Team-ID-Mapping pflegen, H2H über `/getmatchdata/{t1}/{t2}`, historische Ergebnisse in eigene DB spiegeln, H2H-Anzeige aus eigener DB.
10. Täglicher Coroutine-Sync-Job mit `getlastchangedate`-Vergleich für Terminaktualisierung.

**Benchmarks/Schwellen, die Entscheidungen ändern:**
- Wird die App mehrinstanzig deployt → Coroutine-Loop durch Quartz/DB-Lock ersetzen.
- >300 Sheet-Reads/min projektweit → Caching-TTL erhöhen oder Quota-Anhebung beantragen.
- Argon2id-Hash >1 s unter Last → Speicher-/Iterationsparameter senken oder Login-Concurrency stärker limitieren.

---

## Caveats

- **Google OAuth Verification ist das größte Projektrisiko.** Die Dauer (Google-Review „up to 10 days", real 4–6 Wochen inkl. Nachfragen) und die Anforderungen (Datenschutzerklärung, Demo-Video, Domain-Verifikation) werden regelmäßig unterschätzt. Prüfe tagesaktuell im Google Cloud Console Verification Center, ob `spreadsheets.readonly` weiterhin als sensitiv (nicht restricted) gilt – Googles Klassifizierung ändert sich gelegentlich. Ein Wechsel auf `drive.file`+Picker kann die Verification-Hürde erheblich senken.
- Die 7-Tage-Ablaufregel im Testing-Modus (Autorisierungen inkl. Refresh-Token verfallen nach 7 Tagen) macht Produktivbetrieb ohne Veröffentlichung/Verification unmöglich.
- OpenLigaDB ist ein Community-Projekt: Der Team-vs-Team-Endpoint liefert auch von Nutzern angelegte **inoffizielle** Ligen – zwingend nach `leagueShortcut` filtern. Datenaktualität/-verfügbarkeit ist nicht vertraglich garantiert (kein SLA). Für Ausfallsicherheit historische Daten in der eigenen DB spiegeln.
- Feldnamen-Casing bei OpenLigaDB ist inkonsistent (`matchID` groß, `leagueId`/`teamId` klein, `matchIsFinished` statt „matchIdFinished") – exakt übernehmen.
- Google-Sheets-Serial-Werte tragen **keine** Zeitzone; ohne korrekt gesetzte Sheet-Zeitzone (`Europe/Berlin`) und/oder `FORMATTED_STRING`/ISO-Text drohen Verschiebungen (Sommerzeit!).
- Die genannten Versionen (Ktor 3.5.2, Spring Security Crypto 6.3.1, Bouncy Castle 1.78.1, Flyway 10.17.0 etc.) sind Richtwerte zum Stand 08/2026; vor Umsetzung auf die neueste Patch-Version prüfen.
- Mehrere zitierte Argon2-Zeitangaben (0,5–1 s vs. 250–500 ms) stammen aus unterschiedlichen Quellen; final am eigenen Server einmessen.
- API-Football-Free-Tier (100 Requests/Tag) und football-data.org (10 Req/min, 2. Bundesliga NICHT im Free Tier) wurden über Drittquellen (TheStatsAPI, Highlightly, Stand Frühjahr 2026) bestätigt – bei Bedarf direkt auf den Anbieter-Pricing-Seiten gegenprüfen, da sich Tarife ändern können.
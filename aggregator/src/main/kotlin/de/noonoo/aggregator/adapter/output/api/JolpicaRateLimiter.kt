package de.noonoo.aggregator.adapter.output.api

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.plugins.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.ArrayDeque

private val log = KotlinLogging.logger {}

/**
 * Token-Bucket fuer die Jolpica-Rate-Limits (jolpica-f1/docs/rate_limits.md,
 * Stand 2026: "Burst Limit: 4 requests per second. Sustained Limit: 500 requests
 * per hour"). Serialisiert alle Aufrufe eines Clients und wartet blockierend,
 * bis beide Fenster wieder Kapazitaet haben.
 */
class JolpicaRateLimiter(
    private val burstPerSecond: Int = 4,
    private val sustainedPerHour: Int = 500
) {
    private val mutex = Mutex()
    private val secondWindow = ArrayDeque<Long>()
    private val hourWindow = ArrayDeque<Long>()

    suspend fun acquire() {
        mutex.withLock {
            while (true) {
                val now = System.currentTimeMillis()
                while (secondWindow.isNotEmpty() && now - secondWindow.first() > 1_000) secondWindow.removeFirst()
                while (hourWindow.isNotEmpty() && now - hourWindow.first() > 3_600_000) hourWindow.removeFirst()
                if (secondWindow.size < burstPerSecond && hourWindow.size < sustainedPerHour) {
                    secondWindow.addLast(now)
                    hourWindow.addLast(now)
                    return
                }
                val waitMs = if (secondWindow.size >= burstPerSecond) {
                    1_000 - (now - secondWindow.first()) + 5
                } else {
                    3_600_000 - (now - hourWindow.first()) + 50
                }
                delay(waitMs.coerceAtLeast(5))
            }
        }
    }
}

/** Fuehrt [action] rate-limitiert aus und wiederholt bei HTTP 429 mit Backoff (1s/3s/8s). */
suspend fun <T> withJolpicaRetry(limiter: JolpicaRateLimiter, action: suspend () -> T): T {
    val backoffsMs = longArrayOf(1_000, 3_000, 8_000)
    var attempt = 0
    while (true) {
        limiter.acquire()
        try {
            return action()
        } catch (e: ClientRequestException) {
            if (e.response.status.value == 429 && attempt < backoffsMs.size) {
                log.warn { "[F1] 429 Too Many Requests, Retry in ${backoffsMs[attempt]}ms (Versuch ${attempt + 1})" }
                delay(backoffsMs[attempt])
                attempt++
            } else throw e
        }
    }
}

/**
 * Simpler In-Memory-TTL-Cache pro URL, um das Sustained-Limit (500/h) zu schonen.
 * Vergangene Rennergebnisse/Qualifyings sind unter ihrer URL unveraendlich -> lange TTL;
 * "current/last" und Wertungen aendern sich mit jedem Rennen -> kuerzere TTL.
 */
class JolpicaCache {
    private data class Entry(val value: Any?, val expiresAt: Long)

    private val mutex = Mutex()
    private val store = mutableMapOf<String, Entry>()

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> getOrPut(key: String, ttlMs: Long, compute: suspend () -> T): T {
        mutex.withLock {
            val cached = store[key]
            if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
                return cached.value as T
            }
        }
        val result = compute()
        mutex.withLock {
            store[key] = Entry(result, System.currentTimeMillis() + ttlMs)
        }
        return result
    }
}

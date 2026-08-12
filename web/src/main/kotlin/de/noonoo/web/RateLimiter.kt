package de.noonoo.web

import io.ktor.server.application.ApplicationCall
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Berücksichtigt X-Forwarded-For hinter dem Caddy-Reverse-Proxy (docker-compose.yml). */
fun ApplicationCall.clientIp(): String =
    request.headers["X-Forwarded-For"]?.split(",")?.first()?.trim()
        ?: request.local.remoteAddress

/**
 * Fixed-Window-Rate-Limit pro Key (i. d. R. Client-IP). Ursprünglich für die
 * Config-Routen gebaut (enumerierbare Base36-Codes), wird hier auch für die
 * Account-Routen (Brute-Force-Schutz bei Login/Registrierung) wiederverwendet.
 */
class RateLimiter(private val maxPerMinute: Int) {
    private data class Window(val startedAt: Long, val count: AtomicInteger)
    private val windows = ConcurrentHashMap<String, Window>()

    fun allow(key: String): Boolean {
        val now = System.currentTimeMillis()
        val window = windows.compute(key) { _, w ->
            if (w == null || now - w.startedAt > 60_000) Window(now, AtomicInteger(0)) else w
        }!!
        if (windows.size > 10_000) windows.entries.removeIf { now - it.value.startedAt > 60_000 }
        return window.count.incrementAndGet() <= maxPerMinute
    }
}

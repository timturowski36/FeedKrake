package de.noonoo.core.domain.port.output

interface NotificationPort {
    suspend fun send(channel: String, message: String)
}

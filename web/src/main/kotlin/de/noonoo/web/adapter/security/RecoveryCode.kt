package de.noonoo.web.adapter.security

import java.security.SecureRandom

/**
 * Einmalig angezeigter Recovery-Code als Ersatz für den fehlenden Passwort-
 * Reset-Flow (kein E-Mail-Verfahren vorhanden). Alphabet ohne mehrdeutige
 * Zeichen (0/O, 1/I/L), damit Nutzer den Code fehlerfrei abtippen können.
 */
object RecoveryCode {
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private const val LENGTH = 10
    private val random = SecureRandom()

    fun generate(): String = (1..LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
}

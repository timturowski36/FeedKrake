package de.noonoo.web.adapter.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM-Verschlüsselung für Google-Refresh-Tokens (langlebige
 * Zugangsdaten, müssen verschlüsselt statt gehasht werden, da der Klartext
 * für den Token-Refresh wieder gebraucht wird). IV wird pro Verschlüsselung
 * neu erzeugt und dem Ciphertext vorangestellt (IV || Ciphertext+Tag).
 */
class TokenCrypto(keyBytes: ByteArray) {
    private val key = SecretKeySpec(keyBytes, "AES")
    private val random = SecureRandom()

    fun encrypt(plain: String): ByteArray {
        val iv = ByteArray(IV_LENGTH).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
    }

    fun decrypt(blob: ByteArray): String {
        val iv = blob.copyOfRange(0, IV_LENGTH)
        val ciphertext = blob.copyOfRange(IV_LENGTH, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_BITS = 128
    }
}

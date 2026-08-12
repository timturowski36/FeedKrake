package de.noonoo.web.adapter.security

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Argon2id-Hashing direkt über BouncyCastle (reines JVM, keine native lib —
 * wichtig für die Alpine-Docker-Images). Parameter sind das OWASP-Minimum
 * (m=19456 KiB, t=2, p=1) und bewusst fest im Code statt als PHC-String
 * kodiert, da sie sich nicht pro Nutzer unterscheiden.
 */
object PasswordHasher {
    private const val SALT_LENGTH = 16
    private const val HASH_LENGTH = 32
    private const val MEMORY_KIB = 19456
    private const val ITERATIONS = 2
    private const val PARALLELISM = 1
    private val random = SecureRandom()

    data class HashResult(val hash: ByteArray, val salt: ByteArray)

    fun hash(raw: String): HashResult {
        val salt = ByteArray(SALT_LENGTH).also { random.nextBytes(it) }
        return HashResult(hashWithSalt(raw, salt), salt)
    }

    fun verify(raw: String, hash: ByteArray, salt: ByteArray): Boolean =
        MessageDigest.isEqual(hashWithSalt(raw, salt), hash)

    private fun hashWithSalt(raw: String, salt: ByteArray): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(ITERATIONS)
            .withMemoryAsKB(MEMORY_KIB)
            .withParallelism(PARALLELISM)
            .withSalt(salt)
            .build()
        val generator = Argon2BytesGenerator().apply { init(params) }
        val out = ByteArray(HASH_LENGTH)
        generator.generateBytes(raw.toByteArray(Charsets.UTF_8), out, 0, out.size)
        return out
    }

    /**
     * Fester Dummy-Hash für den Login-Pfad bei unbekanntem Usernamen — hält den
     * Argon2id-Aufwand konstant, damit Antwortzeiten nicht verraten, ob ein
     * Username existiert (Timing-Schutz gegen User-Enumeration).
     */
    val dummy: HashResult by lazy { hash("dummy-password-for-constant-time-comparison") }
}

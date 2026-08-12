package de.noonoo.web.application

import de.noonoo.web.adapter.db.UserRepository
import de.noonoo.web.adapter.security.PasswordHasher
import de.noonoo.web.adapter.security.RecoveryCode
import kotlinx.serialization.Serializable

@Serializable
data class AccountUser(val id: Long, val username: String)

@Serializable
data class RegisterResponse(val user: AccountUser, val recoveryCode: String)

@Serializable
data class RecoverResponse(val user: AccountUser, val recoveryCode: String)

private val USERNAME_REGEX = Regex("^[A-Za-z0-9_]{3,32}$")
private const val MIN_PASSWORD_LENGTH = 10

/**
 * Eigenes Username/Passwort-Konto ohne E-Mail und ohne Passwort-Reset-Flow
 * (Änderungsplan Punkt 3). Statt eines Resets gibt es einen einmalig gezeigten
 * Recovery-Code (siehe [RecoveryCode]), der bei Gebrauch rotiert wird.
 */
class AccountService(private val users: UserRepository) {

    fun register(username: String, password: String): RegisterResponse {
        require(USERNAME_REGEX.matches(username)) { "Benutzername muss 3–32 Zeichen sein (Buchstaben, Ziffern, _)." }
        require(password.length >= MIN_PASSWORD_LENGTH) { "Passwort muss mindestens $MIN_PASSWORD_LENGTH Zeichen haben." }
        val usernameCi = username.lowercase()
        val (passwordHash, passwordSalt) = PasswordHasher.hash(password)
        val recoveryCode = RecoveryCode.generate()
        val (recoveryHash, recoverySalt) = PasswordHasher.hash(recoveryCode)
        val id = users.create(username, usernameCi, passwordHash, passwordSalt, recoveryHash, recoverySalt)
            ?: error("Registrierung fehlgeschlagen. Bitte anderen Namen wählen.")
        return RegisterResponse(AccountUser(id, username), recoveryCode)
    }

    /** Generische Fehlermeldung + konstanter Argon2id-Vergleich bei unbekanntem User (Enumeration-/Timing-Schutz). */
    fun login(username: String, password: String): AccountUser {
        val user = users.findByUsernameCi(username.trim().lowercase())
        if (user == null) {
            PasswordHasher.verify(password, PasswordHasher.dummy.hash, PasswordHasher.dummy.salt)
            error("Benutzername oder Passwort ist ungültig.")
        }
        if (!PasswordHasher.verify(password, user.passwordHash, user.passwordSalt)) {
            error("Benutzername oder Passwort ist ungültig.")
        }
        return AccountUser(user.id, user.username)
    }

    fun findById(id: Long): AccountUser? = users.findById(id)?.let { AccountUser(it.id, it.username) }

    fun recover(username: String, recoveryCode: String, newPassword: String): RecoverResponse {
        require(newPassword.length >= MIN_PASSWORD_LENGTH) { "Passwort muss mindestens $MIN_PASSWORD_LENGTH Zeichen haben." }
        val user = users.findByUsernameCi(username.trim().lowercase())
            ?: error("Benutzername oder Recovery-Code ist ungültig.")
        val storedHash = user.recoveryCodeHash ?: error("Benutzername oder Recovery-Code ist ungültig.")
        val storedSalt = user.recoveryCodeSalt ?: error("Benutzername oder Recovery-Code ist ungültig.")
        if (!PasswordHasher.verify(recoveryCode, storedHash, storedSalt)) {
            error("Benutzername oder Recovery-Code ist ungültig.")
        }
        val (passwordHash, passwordSalt) = PasswordHasher.hash(newPassword)
        val newCode = RecoveryCode.generate()
        val (newRecoveryHash, newRecoverySalt) = PasswordHasher.hash(newCode)
        users.resetPassword(user.id, passwordHash, passwordSalt, newRecoveryHash, newRecoverySalt)
        return RecoverResponse(AccountUser(user.id, user.username), newCode)
    }
}

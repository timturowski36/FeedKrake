package de.noonoo.web.adapter.db

import java.sql.ResultSet
import javax.sql.DataSource

data class StoredUser(
    val id: Long,
    val username: String,
    val passwordHash: ByteArray,
    val passwordSalt: ByteArray,
    val recoveryCodeHash: ByteArray?,
    val recoveryCodeSalt: ByteArray?
)

private const val SELECT_COLUMNS =
    "id, username, password_hash, password_salt, recovery_code_hash, recovery_code_salt"

class UserRepository(private val dataSource: DataSource) {

    /** Gibt die neue User-ID zurück, oder null bei Kollision auf username_ci. */
    fun create(
        username: String,
        usernameCi: String,
        passwordHash: ByteArray,
        passwordSalt: ByteArray,
        recoveryCodeHash: ByteArray,
        recoveryCodeSalt: ByteArray
    ): Long? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO users (username, username_ci, password_hash, password_salt, recovery_code_hash, recovery_code_salt)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (username_ci) DO NOTHING
                RETURNING id
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, username)
                stmt.setString(2, usernameCi)
                stmt.setBytes(3, passwordHash)
                stmt.setBytes(4, passwordSalt)
                stmt.setBytes(5, recoveryCodeHash)
                stmt.setBytes(6, recoveryCodeSalt)
                stmt.executeQuery().use { rs -> if (rs.next()) rs.getLong("id") else null }
            }
        }

    fun findByUsernameCi(usernameCi: String): StoredUser? =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT $SELECT_COLUMNS FROM users WHERE username_ci = ?").use { stmt ->
                stmt.setString(1, usernameCi)
                stmt.executeQuery().use { rs -> if (rs.next()) rs.toStoredUser() else null }
            }
        }

    fun findById(id: Long): StoredUser? =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT $SELECT_COLUMNS FROM users WHERE id = ?").use { stmt ->
                stmt.setLong(1, id)
                stmt.executeQuery().use { rs -> if (rs.next()) rs.toStoredUser() else null }
            }
        }

    /** Setzt ein neues Passwort und rotiert gleichzeitig den Recovery-Code (Single-Use). */
    fun resetPassword(
        userId: Long,
        passwordHash: ByteArray,
        passwordSalt: ByteArray,
        newRecoveryCodeHash: ByteArray,
        newRecoveryCodeSalt: ByteArray
    ) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE users SET password_hash = ?, password_salt = ?, recovery_code_hash = ?, recovery_code_salt = ? WHERE id = ?"
            ).use { stmt ->
                stmt.setBytes(1, passwordHash)
                stmt.setBytes(2, passwordSalt)
                stmt.setBytes(3, newRecoveryCodeHash)
                stmt.setBytes(4, newRecoveryCodeSalt)
                stmt.setLong(5, userId)
                stmt.executeUpdate()
            }
        }
    }

    private fun ResultSet.toStoredUser() = StoredUser(
        id = getLong("id"),
        username = getString("username"),
        passwordHash = getBytes("password_hash"),
        passwordSalt = getBytes("password_salt"),
        recoveryCodeHash = getBytes("recovery_code_hash"),
        recoveryCodeSalt = getBytes("recovery_code_salt")
    )
}

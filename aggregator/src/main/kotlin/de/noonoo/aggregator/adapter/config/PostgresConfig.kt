package de.noonoo.aggregator.adapter.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.cdimascio.dotenv.Dotenv
import io.github.oshai.kotlinlogging.KotlinLogging
import org.flywaydb.core.Flyway
import javax.sql.DataSource

private val log = KotlinLogging.logger {}

object PostgresConfig {

    fun createDataSource(env: Dotenv): DataSource {
        val url  = env["POSTGRES_URL"]
        val user = env["POSTGRES_USER"]
        val pass = env["POSTGRES_PASSWORD"]

        val config = HikariConfig().apply {
            jdbcUrl         = url
            username        = user
            password        = pass
            maximumPoolSize = 5
            minimumIdle     = 1
            connectionTimeout = 30_000
            poolName        = "noonoo-pool"
        }
        val ds = HikariDataSource(config)
        log.info { "PostgreSQL verbunden: $url" }
        runMigrations(ds)
        return ds
    }

    private fun runMigrations(ds: DataSource) {
        val flyway = Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .load()
        flyway.repair()
        val result = flyway.migrate()
        log.info { "Flyway: ${result.migrationsExecuted} Migrationen ausgeführt" }
    }
}

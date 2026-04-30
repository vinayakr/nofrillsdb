package com.nofrillsdb.demo

import com.nofrillsdb.contact.GmailService
import com.nofrillsdb.demo.db.DemoSession
import com.nofrillsdb.demo.repository.DemoSessionRepository
import com.fasterxml.uuid.Generators
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.security.SecureRandom
import java.util.UUID

@RestController
@RequestMapping("/api/demo")
class DemoController(
    @Qualifier("provisionJdbcTemplate") private val jdbcTemplate: JdbcTemplate,
    private val demoSessionRepository: DemoSessionRepository,
    private val gmailService: GmailService,
) {

    private val logger = LoggerFactory.getLogger(DemoController::class.java)

    @Value("\${demo.storage-limit-bytes:104857600}")
    private val storageLimitBytes: Long = 104_857_600L

    @Value("\${demo.max-active-sessions:200}")
    private val maxActiveSessions: Int = 200

    @Value("\${provisioning.pool-user:pgbouncer_auth}")
    private val poolUser: String = "pgbouncer_auth"

    @Value("\${provisioning.connection-limit:5}")
    private val connectionLimit: Int = 5

    @Value("\${provisioning.statement-timeout:10s}")
    private val statementTimeout: String = "10s"

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createDemo(): CreateDemoResponse {
        val activeCount = demoSessionRepository.countByCleanedUpFalse()
        if (activeCount >= maxActiveSessions) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Demo capacity reached, please try again later")
        }

        val token = UUID.randomUUID().toString()
        val ulid = Generators.timeBasedEpochGenerator().generate().toString().replace("-", "")
        val role = "demo_$ulid"
        val ownerRole = "demoowner_$ulid"
        val dbName = "demo_$ulid"
        val password = generateSecurePassword()

        try {
            jdbcTemplate.execute(
                """
                DO $$
                BEGIN
                  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = ${pgLiteral(ownerRole)}) THEN
                    EXECUTE 'CREATE ROLE ${quoteIdent(ownerRole)} NOLOGIN';
                  END IF;
                  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = ${pgLiteral(role)}) THEN
                    CREATE ROLE ${quoteIdent(role)} LOGIN PASSWORD ${pgLiteral(password)} CONNECTION LIMIT $connectionLimit;
                  END IF;
                  EXECUTE 'GRANT ${quoteIdent(ownerRole)} TO CURRENT_USER';
                END
                $$;
                """.trimIndent()
            )

            jdbcTemplate.execute("""CREATE DATABASE ${quoteIdent(dbName)} OWNER ${quoteIdent(ownerRole)}""")

            jdbcTemplate.execute("""SET ROLE ${quoteIdent(ownerRole)}""")
            try {
                jdbcTemplate.execute("""REVOKE ALL ON DATABASE ${quoteIdent(dbName)} FROM PUBLIC""")
                jdbcTemplate.execute("""GRANT CONNECT, TEMPORARY ON DATABASE ${quoteIdent(dbName)} TO ${quoteIdent(role)}""")
                jdbcTemplate.execute("""GRANT CONNECT, TEMPORARY ON DATABASE ${quoteIdent(dbName)} TO ${quoteIdent(poolUser)}""")
            } finally {
                jdbcTemplate.execute("""RESET ROLE""")
            }

<<<<<<< Updated upstream
=======
            // Revoke the demo role's access to every database it is not supposed to use.
            // PUBLIC has CONNECT on postgres by default; revoke it explicitly for this role
            // so it cannot be used as a backdoor even if PUBLIC grants remain.
            jdbcTemplate.execute(
                """
                DO $$
                DECLARE
                    db TEXT;
                BEGIN
                    FOR db IN
                        SELECT datname FROM pg_database
                        WHERE datname != ${pgLiteral(dbName)}
                          AND NOT datistemplate
                    LOOP
                        EXECUTE 'REVOKE CONNECT ON DATABASE ' || quote_ident(db) || ' FROM ${quoteIdent(role)}';
                    END LOOP;
                END
                $$;
                """.trimIndent()
            )

>>>>>>> Stashed changes
            val dbJdbc = jdbcForDatabase(dbName)
            try {
                dbJdbc.execute("""REVOKE ALL ON SCHEMA public FROM PUBLIC""")
                dbJdbc.execute("""GRANT USAGE, CREATE ON SCHEMA public TO ${quoteIdent(role)}""")
                dbJdbc.execute("""ALTER SCHEMA public OWNER TO ${quoteIdent(ownerRole)}""")
            } finally {
                val ds = dbJdbc.dataSource as? com.zaxxer.hikari.HikariDataSource
                ds?.close()
            }

            jdbcTemplate.execute(
                """ALTER ROLE ${quoteIdent(role)} SET statement_timeout = ${pgLiteral(statementTimeout)}"""
            )

            demoSessionRepository.save(DemoSession(token = token, role = role, databaseName = dbName, password = password))

            logger.info("Created demo session: database=$dbName")

            runCatching { gmailService.sendDemoSessionCreatedEmail(databaseName = dbName, role = role) }
                .onFailure { logger.error("Failed to send demo session created email", it) }

            return CreateDemoResponse(token = token, role = role, password = password, databaseName = dbName)
        } catch (e: Exception) {
            logger.error("Failed to create demo session, rolling back", e)
            runCatching { jdbcTemplate.execute("""DROP DATABASE IF EXISTS ${quoteIdent(dbName)}""") }
            runCatching { jdbcTemplate.execute("""DROP ROLE IF EXISTS ${quoteIdent(role)}""") }
            runCatching { jdbcTemplate.execute("""DROP ROLE IF EXISTS ${quoteIdent(ownerRole)}""") }
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create demo environment")
        }
    }

    @GetMapping("/{token}")
    fun getDemoStatus(@PathVariable token: String): DemoStatusResponse {
        val session = demoSessionRepository.findById(token).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Demo session not found")
        }

        if (session.cleanedUp) {
            throw ResponseStatusException(HttpStatus.GONE, "Demo session has been cleaned up")
        }

        val sizeBytes = runCatching {
            jdbcTemplate.queryForObject("SELECT pg_database_size(?)", Long::class.java, session.databaseName) ?: 0L
        }.getOrDefault(0L)

        return DemoStatusResponse(
            token = session.token,
            role = session.role,
            password = session.password,
            databaseName = session.databaseName,
            storageExceeded = session.storageExceeded,
            sizeBytes = sizeBytes,
            storageLimitBytes = storageLimitBytes,
        )
    }

    private fun jdbcForDatabase(dbName: String): JdbcTemplate {
        val provisioningHikari = jdbcTemplate.dataSource as com.zaxxer.hikari.HikariDataSource
        val uri = java.net.URI(provisioningHikari.jdbcUrl.removePrefix("jdbc:"))
        val host = uri.host
        val port = if (uri.port == -1) 5432 else uri.port
        val query = uri.rawQuery
        val newUrl = buildString {
            append("jdbc:postgresql://")
            append(host); append(":"); append(port); append("/"); append(dbName)
            if (!query.isNullOrBlank()) { append("?"); append(query) }
        }
        val cfg = com.zaxxer.hikari.HikariConfig().apply {
            jdbcUrl = newUrl
            driverClassName = provisioningHikari.driverClassName
            username = provisioningHikari.username
            password = provisioningHikari.password
            dataSourceProperties.putAll(provisioningHikari.dataSourceProperties)
            maximumPoolSize = 1; minimumIdle = 0; poolName = "demo-$dbName"
        }
        return JdbcTemplate(com.zaxxer.hikari.HikariDataSource(cfg))
    }

    private fun generateSecurePassword(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val random = SecureRandom()
        return (1..20).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }

    private fun quoteIdent(ident: String): String = "\"$ident\""
    private fun pgLiteral(s: String): String = "'" + s.replace("'", "''") + "'"
}

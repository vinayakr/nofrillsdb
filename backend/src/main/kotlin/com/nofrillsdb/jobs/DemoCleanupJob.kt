package com.nofrillsdb.jobs

import com.nofrillsdb.contact.GmailService
import com.nofrillsdb.demo.repository.DemoSessionRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class DemoCleanupJob(
    private val demoSessionRepository: DemoSessionRepository,
    @Qualifier("provisionJdbcTemplate") private val jdbcTemplate: JdbcTemplate,
    private val gmailService: GmailService,
) {

    private val logger = LoggerFactory.getLogger(DemoCleanupJob::class.java)

    @Value("\${demo.storage-limit-bytes:104857600}")
    private val storageLimitBytes: Long = 104_857_600L  // 100MB

    @Value("\${demo.storage-grace-bytes:52428800}")
    private val storageGraceBytes: Long = 52_428_800L   // 50MB

    // Runs every 15 minutes
    @Scheduled(cron = "0 */15 * * * *")
    fun checkStorage() {
        val sessions = demoSessionRepository.findByCleanedUpFalse()
        logger.info("Demo storage check: ${sessions.size} active session(s)")

        for (session in sessions) {
            try {
                val sizeBytes = jdbcTemplate.queryForObject(
                    "SELECT pg_database_size(?)", Long::class.java, session.databaseName
                ) ?: continue

                val hardLimitBytes = storageLimitBytes + storageGraceBytes

                // Hard limit exceeded (150MB): revoke login if role can still log in
                if (sizeBytes > hardLimitBytes) {
                    val canLogin = jdbcTemplate.queryForObject(
                        "SELECT rolcanlogin FROM pg_roles WHERE rolname = ?",
                        Boolean::class.java, session.role
                    ) ?: false

                    if (canLogin) {
                        logger.warn("Demo ${session.databaseName} exceeded hard limit (${sizeBytes}B > ${hardLimitBytes}B), revoking login")
                        jdbcTemplate.execute("""ALTER ROLE "${session.role}" NOLOGIN""")
                    }
                }

                // Soft limit exceeded (100MB): send warning email once
                if (sizeBytes > storageLimitBytes && !session.storageExceeded) {
                    logger.warn("Demo ${session.databaseName} exceeded soft limit: ${sizeBytes}B > ${storageLimitBytes}B")
                    runCatching {
                        gmailService.sendDemoStorageWarningEmail(
                            databaseName = session.databaseName,
                            role = session.role,
                            sizeBytes = sizeBytes,
                            limitBytes = storageLimitBytes,
                        )
                    }.onFailure { logger.error("Failed to send storage warning email for ${session.databaseName}", it) }

                    session.storageExceeded = true
                    demoSessionRepository.save(session)
                }
            } catch (e: Exception) {
                logger.error("Error checking storage for demo session ${session.databaseName}", e)
            }
        }
    }
}

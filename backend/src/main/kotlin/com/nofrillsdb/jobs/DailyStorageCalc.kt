package com.nofrillsdb.jobs

import com.nofrillsdb.usage.db.DailyUsage
import com.nofrillsdb.usage.repository.DailyUsageRepository
import com.nofrillsdb.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class DailyStorageCalc (
    private val repository: DailyUsageRepository,
    private val userRepository: UserRepository,
    @Qualifier("provisionJdbcTemplate") private val jdbcTemplate: JdbcTemplate
) {

    private val logger = LoggerFactory.getLogger(DailyStorageCalc::class.java)

    @Scheduled(cron = "0 5 0 * * *")
    fun updateDailySizes() {
        val yesterday = LocalDate.now().minusDays(1)
        logger.info("Starting daily storage calculation for date: $yesterday")

        try {
            val users = userRepository.findAll()
            var processedUsers = 0
            var totalUsageRecords = 0

            for (user in users) {
                if (user.databases.isEmpty()) {
                    logger.debug("User ${user.id} has no databases, skipping")
                    continue
                }

                val dbNames = user.databases.map { it.name }.toTypedArray()

                val sql = """
                    SELECT datname, pg_database_size(datname) AS size_bytes
                    FROM pg_database
                    WHERE datname = ANY (?)
                """.trimIndent()

                val totalSizeBytes = jdbcTemplate.queryForList(sql, dbNames)
                    .sumOf { row ->
                        (row["size_bytes"] as Number).toLong()
                    }

                // Check if record already exists for this user and date
                val existingUsage = repository.findByUserAndUsageDate(user, yesterday)

                if (existingUsage.isEmpty()) {
                    val dailyUsage = DailyUsage(
                        user = user,
                        usageDate = yesterday,
                        totalBytes = totalSizeBytes
                    )
                    repository.save(dailyUsage)
                    totalUsageRecords++
                    logger.debug("Created usage record for user ${user.id}: ${totalSizeBytes} bytes")
                } else {
                    logger.debug("Usage record already exists for user ${user.id} on $yesterday")
                }

                processedUsers++
            }

            logger.info("Daily storage calculation completed. Processed $processedUsers users, created $totalUsageRecords new usage records for $yesterday")

        } catch (exception: Exception) {
            logger.error("Error during daily storage calculation for $yesterday", exception)
        }
    }

}
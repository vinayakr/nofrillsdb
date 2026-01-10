package com.nofrillsdb.jobs

import com.nofrillsdb.provisioning.Database
import com.nofrillsdb.usage.repository.DailyUsageRepository
import com.nofrillsdb.user.repository.UserRepository
import com.nofrillsdb.users.model.db.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate

@SpringBootTest
@Testcontainers
class DailyStorageCalcTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.jpa.hibernate.ddl-auto") { "create-drop" }

            registry.add("provisioning.datasource.url", postgres::getJdbcUrl)
            registry.add("provisioning.datasource.username", postgres::getUsername)
            registry.add("provisioning.datasource.password", postgres::getPassword)
            registry.add("provisioning.datasource.driver-class-name") { "org.postgresql.Driver" }

            registry.add("provisioning.client-ca-key") { "dummyKeyForTesting" }
            registry.add("provisioning.connection-limit") { "100" }
            registry.add("provisioning.statement-timeout") { "30000" }
            registry.add("provisioning.pool-user") { "testuser" }

            registry.add("spring.task.scheduling.enabled") { "false" }
        }
    }

    @Autowired
    private lateinit var dailyStorageCalc: DailyStorageCalc

    @Autowired
    private lateinit var dailyUsageRepository: DailyUsageRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    @Qualifier("provisionJdbcTemplate")
    private lateinit var provisionJdbcTemplate: JdbcTemplate

    private val createdDbNames = linkedSetOf<String>()

    @BeforeEach
    fun setUp() {
        dailyUsageRepository.deleteAll()
        userRepository.deleteAll()

        cleanupCreatedDatabases()
        createdDbNames.clear()
    }

    @Test
    fun `should calculate daily storage for multiple users with multiple databases`() {
        // Create + seed DBs (sizes are approximate; we will assert using pg_database_size)
        val seedTargets = mapOf(
            "user1_db1_test" to 20_000_000L,
            "user1_db2_test" to 15_000_000L,
            "user2_db1_test" to 30_000_000L,
            "user2_db2_test" to 25_000_000L,
            "user2_db3_test" to 10_000_000L,
            "user3_db1_test" to 40_000_000L
        )
        createTestDatabasesWithApproxSizes(seedTargets)

        val user1 = createUserWithDatabases("user1@test.com", "User One", listOf("user1_db1_test", "user1_db2_test"))
        val user2 = createUserWithDatabases("user2@test.com", "User Two", listOf("user2_db1_test", "user2_db2_test", "user2_db3_test"))
        val user3 = createUserWithDatabases("user3@test.com", "User Three", listOf("user3_db1_test"))

        // Ground truth: ask Postgres what each DB size is *right now*
        val actualSizes = fetchDatabaseSizes(createdDbNames.toList())

        val expectedUser1Total = actualSizes["user1_db1_test"]!! + actualSizes["user1_db2_test"]!!
        val expectedUser2Total = actualSizes["user2_db1_test"]!! + actualSizes["user2_db2_test"]!! + actualSizes["user2_db3_test"]!!
        val expectedUser3Total = actualSizes["user3_db1_test"]!!

        dailyStorageCalc.updateDailySizes()

        val usageDate = LocalDate.now().minusDays(1)

        val user1Records = dailyUsageRepository.findByUserAndUsageDate(user1, usageDate)
        val user2Records = dailyUsageRepository.findByUserAndUsageDate(user2, usageDate)
        val user3Records = dailyUsageRepository.findByUserAndUsageDate(user3, usageDate)

        assertThat(user1Records).hasSize(1)
        assertThat(user2Records).hasSize(1)
        assertThat(user3Records).hasSize(1)

        // Use exact match (same function), but allow tiny tolerance just in case your job rounds/types differently.
        assertClose(user1Records[0].totalBytes, expectedUser1Total, toleranceBytes = 128 * 1024)
        assertClose(user2Records[0].totalBytes, expectedUser2Total, toleranceBytes = 128 * 1024)
        assertClose(user3Records[0].totalBytes, expectedUser3Total, toleranceBytes = 128 * 1024)

        assertThat(user1Records[0].usageDate).isEqualTo(usageDate)
        assertThat(user2Records[0].usageDate).isEqualTo(usageDate)
        assertThat(user3Records[0].usageDate).isEqualTo(usageDate)
    }

    @Test
    fun `should skip users with no databases`() {
        val userWithoutDbs = createUserWithDatabases("nodbs@test.com", "No DBs User", emptyList())

        createTestDatabasesWithApproxSizes(mapOf("test_db" to 10_000_000L))
        val userWithDbs = createUserWithDatabases("withdbs@test.com", "With DBs User", listOf("test_db"))

        dailyStorageCalc.updateDailySizes()

        val usageDate = LocalDate.now().minusDays(1)

        assertThat(dailyUsageRepository.findByUserAndUsageDate(userWithoutDbs, usageDate)).isEmpty()

        val withDbRecords = dailyUsageRepository.findByUserAndUsageDate(userWithDbs, usageDate)
        assertThat(withDbRecords).hasSize(1)

        val actual = fetchDatabaseSizes(listOf("test_db"))["test_db"]!!
        assertClose(withDbRecords[0].totalBytes, actual, toleranceBytes = 128 * 1024)
    }

    @Test
    fun `should not create duplicate records when run multiple times`() {
        createTestDatabasesWithApproxSizes(mapOf("duplicate_test_db" to 10_000_000L))
        val user = createUserWithDatabases("duplicate@test.com", "Duplicate Test User", listOf("duplicate_test_db"))

        val usageDate = LocalDate.now().minusDays(1)

        dailyStorageCalc.updateDailySizes()
        val first = dailyUsageRepository.findByUserAndUsageDate(user, usageDate)
        assertThat(first).hasSize(1)
        val firstBytes = first[0].totalBytes

        dailyStorageCalc.updateDailySizes()
        val second = dailyUsageRepository.findByUserAndUsageDate(user, usageDate)
        assertThat(second).hasSize(1)
        assertThat(second[0].totalBytes).isEqualTo(firstBytes)

        assertThat(dailyUsageRepository.count()).isEqualTo(1L)
    }

    @Test
    fun `should handle users with databases that no longer exist in PostgreSQL`() {
        val userWithMissing = createUserWithDatabases(
            "missing@test.com",
            "Missing DBs User",
            listOf("nonexistent_db1", "nonexistent_db2")
        )

        createTestDatabasesWithApproxSizes(mapOf("existing_db" to 10_000_000L))
        val userWithExisting = createUserWithDatabases("existing@test.com", "Existing DB User", listOf("existing_db"))

        dailyStorageCalc.updateDailySizes()

        val usageDate = LocalDate.now().minusDays(1)

        val missingRecords = dailyUsageRepository.findByUserAndUsageDate(userWithMissing, usageDate)
        assertThat(missingRecords).hasSize(1)
        assertThat(missingRecords[0].totalBytes).isEqualTo(0L)

        val existingRecords = dailyUsageRepository.findByUserAndUsageDate(userWithExisting, usageDate)
        assertThat(existingRecords).hasSize(1)

        val actual = fetchDatabaseSizes(listOf("existing_db"))["existing_db"]!!
        assertClose(existingRecords[0].totalBytes, actual, toleranceBytes = 128 * 1024)
    }

    @Test
    fun `should handle calculation when no users exist`() {
        assertThat(userRepository.findAll()).isEmpty()
        dailyStorageCalc.updateDailySizes()
        assertThat(dailyUsageRepository.findAll()).isEmpty()
    }

    @Test
    fun `should use correct date for usage calculation`() {
        createTestDatabasesWithApproxSizes(mapOf("date_test_db" to 10_000_000L))
        val user = createUserWithDatabases("datetest@test.com", "Date Test User", listOf("date_test_db"))

        val expectedDate = LocalDate.now().minusDays(1)

        dailyStorageCalc.updateDailySizes()

        val records = dailyUsageRepository.findByUserAndUsageDate(user, expectedDate)
        assertThat(records).hasSize(1)
        assertThat(records[0].usageDate).isEqualTo(expectedDate)
        assertThat(dailyUsageRepository.findByUserAndUsageDate(user, LocalDate.now())).isEmpty()
    }

    /* ============================================================
     * Helpers
     * ============================================================ */

    private fun createUserWithDatabases(email: String, name: String, databaseNames: List<String>): User {
        val databases = databaseNames.map { Database(it) }.toMutableSet()
        val user = User(email = email, name = name, databases = databases)
        return userRepository.save(user)
    }

    private fun createTestDatabasesWithApproxSizes(dbSizes: Map<String, Long>) {
        dbSizes.forEach { (dbName, targetBytes) ->
            try {
                provisionJdbcTemplate.execute("""CREATE DATABASE "$dbName"""")
                createdDbNames += dbName

                createJdbcTemplateForDatabase(dbName).use { dbJdbc ->
                    generateDataForSize(dbJdbc, targetBytes)
                }
            } catch (e: Exception) {
                println("Warning: Could not create/seed test database $dbName: ${e.message}")
            }
        }
    }

    /**
     * Ground-truth database sizes from Postgres.
     */
    private fun fetchDatabaseSizes(dbNames: List<String>): Map<String, Long> {
        if (dbNames.isEmpty()) return emptyMap()

        val sql = """
            SELECT datname, pg_database_size(datname) AS size_bytes
            FROM pg_database
            WHERE datname = ANY (?)
        """.trimIndent()

        val arr = dbNames.toTypedArray()
        return provisionJdbcTemplate.queryForList(sql, arr)
            .associate {
                val name = it["datname"] as String
                val size = (it["size_bytes"] as Number).toLong()
                name to size
            }
    }

    private fun createJdbcTemplateForDatabase(dbName: String): CloseableJdbc {
        val provisioningHikari = provisionJdbcTemplate.dataSource as com.zaxxer.hikari.HikariDataSource
        val originalUrl = provisioningHikari.jdbcUrl
        val newUrl = originalUrl.substringBeforeLast("/") + "/$dbName"

        val cfg = com.zaxxer.hikari.HikariConfig().apply {
            jdbcUrl = newUrl
            driverClassName = provisioningHikari.driverClassName
            username = provisioningHikari.username
            password = provisioningHikari.password
            dataSourceProperties.putAll(provisioningHikari.dataSourceProperties)
            maximumPoolSize = 1
            minimumIdle = 0
            poolName = "test-$dbName"
        }

        val ds = com.zaxxer.hikari.HikariDataSource(cfg)
        return CloseableJdbc(JdbcTemplate(ds), ds)
    }

    private fun generateDataForSize(jdbcTemplate: JdbcTemplate, targetSizeBytes: Long) {
        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS size_test_data (
                id SERIAL PRIMARY KEY,
                data TEXT
            )
            """.trimIndent()
        )

        val dataPerRow = 2048 // ~2KB
        val bulkData = "x".repeat(dataPerRow)
        val estimatedRows = (targetSizeBytes / dataPerRow).toInt().coerceAtLeast(1)

        val chunkSize = 500
        var inserted = 0
        while (inserted < estimatedRows) {
            val batchSize = minOf(chunkSize, estimatedRows - inserted)
            val valuesClause = (1..batchSize).joinToString(",") { "(?)" }
            val sql = "INSERT INTO size_test_data (data) VALUES $valuesClause"
            val args = Array(batchSize) { bulkData }
            jdbcTemplate.update(sql, *args)
            inserted += batchSize
        }

        jdbcTemplate.execute("ANALYZE size_test_data")
    }

    private fun cleanupCreatedDatabases() {
        createdDbNames.toList().asReversed().forEach { dbName ->
            try {
                provisionJdbcTemplate.execute(
                    """
                    SELECT pg_terminate_backend(pid)
                    FROM pg_stat_activity
                    WHERE datname = ${pgLiteral(dbName)} AND pid <> pg_backend_pid()
                    """.trimIndent()
                )
                provisionJdbcTemplate.execute("""DROP DATABASE IF EXISTS "$dbName"""")
            } catch (_: Exception) {
            }
        }
    }

    private fun assertClose(actual: Long, expected: Long, toleranceBytes: Long) {
        val delta = kotlin.math.abs(actual - expected)
        assertThat(delta)
            .withFailMessage("Expected $actual to be within $toleranceBytes bytes of $expected (delta=$delta)")
            .isLessThanOrEqualTo(toleranceBytes)
    }

    private fun pgLiteral(s: String): String = "'" + s.replace("'", "''") + "'"

    private data class CloseableJdbc(
        val jdbc: JdbcTemplate,
        val ds: com.zaxxer.hikari.HikariDataSource
    ) : AutoCloseable {
        fun <T> use(block: (JdbcTemplate) -> T): T {
            try {
                return block(jdbc)
            } finally {
                close()
            }
        }

        override fun close() {
            try {
                ds.close()
            } catch (_: Exception) {
            }
        }
    }
}
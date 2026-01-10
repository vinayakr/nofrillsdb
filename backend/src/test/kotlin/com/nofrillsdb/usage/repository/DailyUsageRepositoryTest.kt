package com.nofrillsdb.usage.repository

import com.nofrillsdb.provisioning.Database
import com.nofrillsdb.usage.db.DailyUsage
import com.nofrillsdb.user.repository.UserRepository
import com.nofrillsdb.users.model.db.User
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import kotlin.math.sin
import kotlin.random.Random
import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.testcontainers.containers.PostgreSQLContainer

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DailyUsageRepositoryTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:18")
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
        }
    }

    @Autowired
    private lateinit var dailyUsageRepository: DailyUsageRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Test
    fun `should find daily usage by user and date with 30 days of test data`() {
        // Create test users with databases
        val user1 = createUserWithDatabases("user1@example.com", "User One", listOf("db1_user1", "db2_user1"))
        val user2 = createUserWithDatabases("user2@example.com", "User Two", listOf("db1_user2"))
        val user3 = createUserWithDatabases("user3@example.com", "User Three", listOf("db1_user3", "db2_user3", "db3_user3"))

        val users = listOf(user1, user2, user3)

        // Generate 30 days of usage data
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(29)

        val usageRecords = mutableListOf<DailyUsage>()

        for (dayOffset in 0..29) {
            val currentDate = startDate.plusDays(dayOffset.toLong())

            users.forEachIndexed { userIndex, user ->
                val dailyUsage = generateDailyUsage(user, currentDate, userIndex, dayOffset)
                usageRecords.add(dailyUsage)
            }
        }

        // Save all usage records
        dailyUsageRepository.saveAll(usageRecords)

        // Test finding usage by user and date
        val testDate = startDate.plusDays(15) // Middle of the range

        // Test user1
        val user1Usage = dailyUsageRepository.findByUserAndUsageDate(user1, testDate)
        assertThat(user1Usage).hasSize(1)
        assertThat(user1Usage[0].user.email).isEqualTo("user1@example.com")
        assertThat(user1Usage[0].usageDate).isEqualTo(testDate)
        assertThat(user1Usage[0].totalBytes).isGreaterThan(0)

        // Test user2
        val user2Usage = dailyUsageRepository.findByUserAndUsageDate(user2, testDate)
        assertThat(user2Usage).hasSize(1)
        assertThat(user2Usage[0].user.email).isEqualTo("user2@example.com")

        // Test user3
        val user3Usage = dailyUsageRepository.findByUserAndUsageDate(user3, testDate)
        assertThat(user3Usage).hasSize(1)
        assertThat(user3Usage[0].user.email).isEqualTo("user3@example.com")

        // Test non-existent date
        val nonExistentDate = endDate.plusDays(1)
        val noUsage = dailyUsageRepository.findByUserAndUsageDate(user1, nonExistentDate)
        assertThat(noUsage).isEmpty()
    }

    @Test
    fun `should handle multiple users with varying usage patterns over 30 days`() {
        // Create users with different usage patterns
        val lightUser = createUserWithDatabases("light@example.com", "Light User", listOf("small_db"))
        val mediumUser = createUserWithDatabases("medium@example.com", "Medium User", listOf("medium_db1", "medium_db2"))
        val heavyUser = createUserWithDatabases("heavy@example.com", "Heavy User", listOf("large_db1", "large_db2", "large_db3", "large_db4"))

        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(29)

        // Generate usage data with different patterns
        val usageRecords = mutableListOf<DailyUsage>()

        for (dayOffset in 0..29) {
            val currentDate = startDate.plusDays(dayOffset.toLong())

            // Light user: consistent small usage (1-10 MB)
            val lightUsage = DailyUsage(
                user = lightUser,
                usageDate = currentDate,
                totalBytes = Random.nextLong(1_000_000, 10_000_000) // 1-10 MB
            )

            // Medium user: moderate usage with weekly spikes (10-100 MB, spikes to 500 MB)
            val isWeeklySpike = dayOffset % 7 == 0
            val mediumUsage = DailyUsage(
                user = mediumUser,
                usageDate = currentDate,
                totalBytes = if (isWeeklySpike) {
                    Random.nextLong(400_000_000, 500_000_000) // 400-500 MB spike
                } else {
                    Random.nextLong(10_000_000, 100_000_000) // 10-100 MB normal
                }
            )

            // Heavy user: large usage with growth trend (100 MB - 10 GB)
            val growthFactor = 1 + (dayOffset * 0.05) // 5% daily growth
            val baseUsage = 100_000_000L // 100 MB base
            val heavyUsage = DailyUsage(
                user = heavyUser,
                usageDate = currentDate,
                totalBytes = (baseUsage * growthFactor).toLong().coerceAtMost(10_000_000_000L) // Max 10 GB
            )

            usageRecords.addAll(listOf(lightUsage, mediumUsage, heavyUsage))
        }

        dailyUsageRepository.saveAll(usageRecords)

        // Verify data patterns
        val midDate = startDate.plusDays(15)

        // Light user should have consistent small usage
        val lightUsageRecord = dailyUsageRepository.findByUserAndUsageDate(lightUser, midDate)
        assertThat(lightUsageRecord).hasSize(1)
        assertThat(lightUsageRecord[0].totalBytes).isBetween(1_000_000L, 10_000_000L)

        // Medium user usage should vary
        val mediumUsageRecord = dailyUsageRepository.findByUserAndUsageDate(mediumUser, midDate)
        assertThat(mediumUsageRecord).hasSize(1)
        assertThat(mediumUsageRecord[0].totalBytes).isGreaterThan(10_000_000L)

        // Heavy user should have large usage
        val heavyUsageRecord = dailyUsageRepository.findByUserAndUsageDate(heavyUser, midDate)
        assertThat(heavyUsageRecord).hasSize(1)
        assertThat(heavyUsageRecord[0].totalBytes).isGreaterThan(100_000_000L)

        // Verify total count of records
        val allRecords = dailyUsageRepository.findAll()
        assertThat(allRecords).hasSize(90) // 3 users * 30 days
    }

    @Test
    fun `should find usage records within date range`() {
        val user = createUserWithDatabases("range@example.com", "Range User", listOf("range_db"))

        val baseDate = LocalDate.of(2024, 1, 1)
        val usageRecords = mutableListOf<DailyUsage>()

        // Create 30 days of data starting from a fixed date
        for (dayOffset in 0..29) {
            val currentDate = baseDate.plusDays(dayOffset.toLong())
            val dailyUsage = DailyUsage(
                user = user,
                usageDate = currentDate,
                totalBytes = generateSinusoidalUsage(dayOffset, 1_000_000_000L) // Base 1GB with variation
            )
            usageRecords.add(dailyUsage)
        }

        dailyUsageRepository.saveAll(usageRecords)

        // Test finding specific dates
        val firstWeek = (0..6).map { baseDate.plusDays(it.toLong()) }

        for (date in firstWeek) {
            val usage = dailyUsageRepository.findByUserAndUsageDate(user, date)
            assertThat(usage).hasSize(1)
            assertThat(usage[0].usageDate).isEqualTo(date)
        }
    }

    @Test
    fun `should handle partial month usage where user only has data for last 10 days`() {
        // Create a new user who joined mid-month
        val newUser = createUserWithDatabases("newuser@example.com", "New User", listOf("new_db1", "new_db2"))

        // Existing user who has been around the entire month
        val existingUser = createUserWithDatabases("existing@example.com", "Existing User", listOf("existing_db"))

        val monthStart = LocalDate.of(2024, 3, 1) // March 1st
        val monthEnd = LocalDate.of(2024, 3, 31)  // March 31st
        val newUserStartDate = LocalDate.of(2024, 3, 22) // User joined March 22nd (last 10 days)

        val usageRecords = mutableListOf<DailyUsage>()

        // Generate full month data for existing user (31 days)
        for (dayOfMonth in 1..31) {
            val currentDate = monthStart.plusDays(dayOfMonth - 1L)
            val existingUserUsage = DailyUsage(
                user = existingUser,
                usageDate = currentDate,
                totalBytes = Random.nextLong(50_000_000, 200_000_000) // 50-200 MB
            )
            usageRecords.add(existingUserUsage)
        }

        // Generate partial month data for new user (only last 10 days: March 22-31)
        for (dayOfMonth in 22..31) {
            val currentDate = monthStart.plusDays(dayOfMonth - 1L)
            val newUserUsage = DailyUsage(
                user = newUser,
                usageDate = currentDate,
                totalBytes = Random.nextLong(10_000_000, 50_000_000) // 10-50 MB (new user, smaller usage)
            )
            usageRecords.add(newUserUsage)
        }

        dailyUsageRepository.saveAll(usageRecords)

        // Test ALL 31 days of March for both users
        for (dayOfMonth in 1..31) {
            val currentDate = monthStart.plusDays(dayOfMonth - 1L)

            // Existing user should have data for ALL days
            val existingUserData = dailyUsageRepository.findByUserAndUsageDate(existingUser, currentDate)
            assertThat(existingUserData)
                .withFailMessage("Existing user should have data for March $dayOfMonth")
                .hasSize(1)
            assertThat(existingUserData[0].user.email).isEqualTo("existing@example.com")
            assertThat(existingUserData[0].totalBytes).isGreaterThan(0)

            // New user should only have data for days 22-31 (last 10 days)
            val newUserData = dailyUsageRepository.findByUserAndUsageDate(newUser, currentDate)
            if (dayOfMonth < 22) {
                // Days 1-21: new user should have NO data
                assertThat(newUserData)
                    .withFailMessage("New user should NOT have data for March $dayOfMonth (before join date)")
                    .isEmpty()
            } else {
                // Days 22-31: new user should have data
                assertThat(newUserData)
                    .withFailMessage("New user should have data for March $dayOfMonth (after join date)")
                    .hasSize(1)
                assertThat(newUserData[0].user.email).isEqualTo("newuser@example.com")
                assertThat(newUserData[0].totalBytes).isGreaterThan(0)
                assertThat(newUserData[0].usageDate).isEqualTo(currentDate)
            }
        }

        // Verify correct total counts
        val allNewUserRecords = dailyUsageRepository.findAll().filter { it.user.email == "newuser@example.com" }
        assertThat(allNewUserRecords).hasSize(10) // Only 10 days of data

        val allExistingUserRecords = dailyUsageRepository.findAll().filter { it.user.email == "existing@example.com" }
        assertThat(allExistingUserRecords).hasSize(31) // Full month of data

        // Test edge case: day before new user joined
        val dayBeforeJoin = dailyUsageRepository.findByUserAndUsageDate(newUser, LocalDate.of(2024, 3, 21))
        assertThat(dayBeforeJoin).isEmpty()

        // Test edge case: day after month ends
        val dayAfterMonth = dailyUsageRepository.findByUserAndUsageDate(newUser, LocalDate.of(2024, 4, 1))
        assertThat(dayAfterMonth).isEmpty()

        // Verify that both users can have data on the same day (overlapping period)
        val overlappingDate = LocalDate.of(2024, 3, 25)
        val existingUserOverlap = dailyUsageRepository.findByUserAndUsageDate(existingUser, overlappingDate)
        val newUserOverlap = dailyUsageRepository.findByUserAndUsageDate(newUser, overlappingDate)

        assertThat(existingUserOverlap).hasSize(1)
        assertThat(newUserOverlap).hasSize(1)
        assertThat(existingUserOverlap[0].user.id).isNotEqualTo(newUserOverlap[0].user.id)
    }

    @Test
    fun `should calculate monthly usage based on average daily data for full month`() {
        val user = createUserWithDatabases("fullmonth@example.com", "Full Month User", listOf("monthly_db1", "monthly_db2"))

        val monthStart = LocalDate.of(2024, 4, 1) // April 1st
        val monthEnd = LocalDate.of(2024, 4, 30)  // April 30th (30 days)
        val usageRecords = mutableListOf<DailyUsage>()

        // Generate realistic usage data with variation for all 30 days of April
        val baseUsagePerDay = 100_000_000L // 100 MB base
        var totalUsageBytes = 0L

        for (dayOfMonth in 1..30) {
            val currentDate = monthStart.plusDays(dayOfMonth - 1L)

            // Create realistic daily variation (weekdays vs weekends, business patterns)
            val dayOfWeek = currentDate.dayOfWeek.value // 1=Monday, 7=Sunday
            val isWeekend = dayOfWeek in 6..7
            val isBusinessPeak = dayOfWeek in 2..4 // Tue-Thu are peak business days

            val dailyBytes = when {
                isWeekend -> (baseUsagePerDay * 0.3).toLong() // 30% usage on weekends
                isBusinessPeak -> (baseUsagePerDay * 1.5).toLong() // 150% on peak days
                else -> baseUsagePerDay // 100% on normal weekdays
            }

            // Add some random variation (±10%)
            val randomVariation = Random.nextDouble(0.9, 1.1)
            val finalDailyBytes = (dailyBytes * randomVariation).toLong()

            totalUsageBytes += finalDailyBytes

            val dailyUsage = DailyUsage(
                user = user,
                usageDate = currentDate,
                totalBytes = finalDailyBytes
            )
            usageRecords.add(dailyUsage)
        }

        dailyUsageRepository.saveAll(usageRecords)

        // Calculate monthly usage metrics
        val allUserRecords = dailyUsageRepository.findAll().filter { it.user.email == "fullmonth@example.com" }
        assertThat(allUserRecords).hasSize(30) // Verify all 30 days present

        val actualTotalUsage = allUserRecords.sumOf { it.totalBytes }
        val actualAverageDaily = actualTotalUsage / 30
        val actualPeakDaily = allUserRecords.maxOf { it.totalBytes }
        val actualMinDaily = allUserRecords.minOf { it.totalBytes }

        // Verify calculations match our data generation
        assertThat(actualTotalUsage).isEqualTo(totalUsageBytes)
        assertThat(actualAverageDaily).isBetween(
            (baseUsagePerDay * 0.8).toLong(), // Account for weekends bringing down average
            (baseUsagePerDay * 1.2).toLong()  // Account for peak days bringing up average
        )

        // Verify realistic patterns
        assertThat(actualPeakDaily).isGreaterThan(actualAverageDaily) // Peak should be higher than average
        assertThat(actualMinDaily).isLessThan(actualAverageDaily)     // Weekend usage should be lower

        // Monthly totals should be reasonable for business usage patterns
        val monthlyTotalGB = actualTotalUsage.toDouble() / (1024 * 1024 * 1024)
        assertThat(monthlyTotalGB).isBetween(2.0, 5.0) // ~2-5 GB total monthly usage

        // Test specific date queries work correctly
        val midMonthDate = LocalDate.of(2024, 4, 15)
        val midMonthUsage = dailyUsageRepository.findByUserAndUsageDate(user, midMonthDate)
        assertThat(midMonthUsage).hasSize(1)
        assertThat(midMonthUsage[0].totalBytes).isGreaterThan(0)

        println("Full Month Statistics:")
        println("Total Usage: ${String.format("%.2f", monthlyTotalGB)} GB")
        println("Average Daily: ${actualAverageDaily / (1024 * 1024)} MB")
        println("Peak Daily: ${actualPeakDaily / (1024 * 1024)} MB")
        println("Min Daily: ${actualMinDaily / (1024 * 1024)} MB")
    }

    @Test
    fun `should calculate monthly usage based on average daily data for partial month`() {
        val user = createUserWithDatabases("partialmonth@example.com", "Partial Month User", listOf("partial_db"))

        val monthStart = LocalDate.of(2024, 5, 1)   // May 1st
        val userJoinDate = LocalDate.of(2024, 5, 20) // User joined May 20th (12 days remaining: 20-31)
        val monthEnd = LocalDate.of(2024, 5, 31)    // May 31st (31 days total)
        val usageRecords = mutableListOf<DailyUsage>()

        // Generate usage data only for the last 12 days (May 20-31)
        val baseUsagePerDay = 150_000_000L // 150 MB base
        var actualTotalUsage = 0L
        val actualDaysWithData = 12

        for (dayOfMonth in 20..31) {
            val currentDate = monthStart.plusDays(dayOfMonth - 1L)

            // Simulate new user ramping up usage over time
            val daysSinceJoin = dayOfMonth - 20
            val rampUpFactor = 0.5 + (daysSinceJoin * 0.1) // Start at 50%, increase 10% daily

            val dailyBytes = (baseUsagePerDay * rampUpFactor).toLong()

            // Add random variation (±15% for new user uncertainty)
            val randomVariation = Random.nextDouble(0.85, 1.15)
            val finalDailyBytes = (dailyBytes * randomVariation).toLong()

            actualTotalUsage += finalDailyBytes

            val dailyUsage = DailyUsage(
                user = user,
                usageDate = currentDate,
                totalBytes = finalDailyBytes
            )
            usageRecords.add(dailyUsage)
        }

        dailyUsageRepository.saveAll(usageRecords)

        // Calculate partial month metrics - ONLY for May 2024
        val mayStart = LocalDate.of(2024, 5, 1)
        val mayEnd = LocalDate.of(2024, 5, 31)
        val allUserRecords = dailyUsageRepository.findAll()
            .filter { it.user.email == "partialmonth@example.com" }
            .filter { it.usageDate in mayStart..mayEnd }
        assertThat(allUserRecords).hasSize(12) // Only 12 days of data in May

        val calculatedTotalUsage = allUserRecords.sumOf { it.totalBytes }
        val averageDailyUsage = calculatedTotalUsage / actualDaysWithData
        val peakDaily = allUserRecords.maxOf { it.totalBytes }
        val minDaily = allUserRecords.minOf { it.totalBytes }

        // Verify calculations
        assertThat(calculatedTotalUsage).isEqualTo(actualTotalUsage)
        assertThat(allUserRecords.size).isEqualTo(actualDaysWithData)

        // Calculate projected monthly usage (if user had been there full month)
        val projectedMonthlyUsage = averageDailyUsage * 31 // Full month projection
        val partialMonthPercentage = (actualDaysWithData.toDouble() / 31) * 100

        // Verify usage patterns show growth (new user ramping up)
        val firstDayUsage = allUserRecords.minByOrNull { it.usageDate }?.totalBytes ?: 0
        val lastDayUsage = allUserRecords.maxByOrNull { it.usageDate }?.totalBytes ?: 0
        assertThat(lastDayUsage).isGreaterThan(firstDayUsage) // Should show growth pattern

        // Verify reasonable ranges for partial month
        assertThat(averageDailyUsage).isBetween(
            (baseUsagePerDay * 0.6).toLong(), // Lower end due to ramp-up
            (baseUsagePerDay * 1.2).toLong()  // Upper end after growth
        )

        // Test edge cases: verify no data before join date
        for (dayOfMonth in 1..19) {
            val dateBeforeJoin = monthStart.plusDays(dayOfMonth - 1L)
            val noData = dailyUsageRepository.findByUserAndUsageDate(user, dateBeforeJoin)
            assertThat(noData).isEmpty()
        }

        // Test edge cases: verify data exists after join date
        for (dayOfMonth in 20..31) {
            val dateAfterJoin = monthStart.plusDays(dayOfMonth - 1L)
            val hasData = dailyUsageRepository.findByUserAndUsageDate(user, dateAfterJoin)
            assertThat(hasData).hasSize(1)
        }

        val partialUsageGB = calculatedTotalUsage.toDouble() / (1024 * 1024 * 1024)
        val projectedUsageGB = projectedMonthlyUsage.toDouble() / (1024 * 1024 * 1024)

        println("Partial Month Statistics:")
        println("Days Active: $actualDaysWithData out of 31 (${String.format("%.1f", partialMonthPercentage)}%)")
        println("Actual Partial Usage: ${String.format("%.3f", partialUsageGB)} GB")
        println("Projected Full Month: ${String.format("%.3f", projectedUsageGB)} GB")
        println("Average Daily: ${averageDailyUsage / (1024 * 1024)} MB")
        println("Growth: ${firstDayUsage / (1024 * 1024)} MB → ${lastDayUsage / (1024 * 1024)} MB")
    }

    @Test
    fun `should calculate monthly usage for user with data spanning multiple months`() {
        val user = createUserWithDatabases("multimonth@example.com", "Multi Month User", listOf("historical_db"))

        val usageRecords = mutableListOf<DailyUsage>()

        // Generate data for 3 months: March, April, May 2024
        val march2024 = LocalDate.of(2024, 3, 1)
        val april2024 = LocalDate.of(2024, 4, 1)
        val may2024 = LocalDate.of(2024, 5, 1)

        // March 2024: Full month (31 days) - Lower usage
        for (dayOfMonth in 1..31) {
            val currentDate = march2024.plusDays(dayOfMonth - 1L)
            val dailyUsage = DailyUsage(
                user = user,
                usageDate = currentDate,
                totalBytes = Random.nextLong(30_000_000, 80_000_000) // 30-80 MB
            )
            usageRecords.add(dailyUsage)
        }

        // April 2024: Full month (30 days) - Medium usage
        for (dayOfMonth in 1..30) {
            val currentDate = april2024.plusDays(dayOfMonth - 1L)
            val dailyUsage = DailyUsage(
                user = user,
                usageDate = currentDate,
                totalBytes = Random.nextLong(80_000_000, 150_000_000) // 80-150 MB
            )
            usageRecords.add(dailyUsage)
        }

        // May 2024: Partial month (only first 15 days) - Higher usage
        for (dayOfMonth in 1..15) {
            val currentDate = may2024.plusDays(dayOfMonth - 1L)
            val dailyUsage = DailyUsage(
                user = user,
                usageDate = currentDate,
                totalBytes = Random.nextLong(150_000_000, 250_000_000) // 150-250 MB
            )
            usageRecords.add(dailyUsage)
        }

        dailyUsageRepository.saveAll(usageRecords)

        // Calculate usage for each month separately using date filtering

        // March 2024 calculation
        val marchStart = LocalDate.of(2024, 3, 1)
        val marchEnd = LocalDate.of(2024, 3, 31)
        val marchRecords = dailyUsageRepository.findAll()
            .filter { it.user.email == "multimonth@example.com" }
            .filter { it.usageDate in marchStart..marchEnd }

        assertThat(marchRecords).hasSize(31) // Full March
        val marchTotalUsage = marchRecords.sumOf { it.totalBytes }
        val marchAverageDaily = marchTotalUsage / 31
        val marchUsageGB = marchTotalUsage.toDouble() / (1024 * 1024 * 1024)

        // April 2024 calculation
        val aprilStart = LocalDate.of(2024, 4, 1)
        val aprilEnd = LocalDate.of(2024, 4, 30)
        val aprilRecords = dailyUsageRepository.findAll()
            .filter { it.user.email == "multimonth@example.com" }
            .filter { it.usageDate in aprilStart..aprilEnd }

        assertThat(aprilRecords).hasSize(30) // Full April
        val aprilTotalUsage = aprilRecords.sumOf { it.totalBytes }
        val aprilAverageDaily = aprilTotalUsage / 30
        val aprilUsageGB = aprilTotalUsage.toDouble() / (1024 * 1024 * 1024)

        // May 2024 calculation (partial month)
        val mayStart = LocalDate.of(2024, 5, 1)
        val mayEnd = LocalDate.of(2024, 5, 31)
        val mayRecords = dailyUsageRepository.findAll()
            .filter { it.user.email == "multimonth@example.com" }
            .filter { it.usageDate in mayStart..mayEnd }

        assertThat(mayRecords).hasSize(15) // Partial May (15 days)
        val mayTotalUsage = mayRecords.sumOf { it.totalBytes }
        val mayAverageDaily = mayTotalUsage / 15
        val mayActualUsageGB = mayTotalUsage.toDouble() / (1024 * 1024 * 1024)
        val mayProjectedUsageGB = (mayAverageDaily * 31).toDouble() / (1024 * 1024 * 1024)

        // Verify growth pattern: March < April < May (average daily usage)
        assertThat(marchAverageDaily).isLessThan(aprilAverageDaily)
        assertThat(aprilAverageDaily).isLessThan(mayAverageDaily)

        // Verify we have no data for other months
        val februaryRecords = dailyUsageRepository.findAll()
            .filter { it.user.email == "multimonth@example.com" }
            .filter { it.usageDate.monthValue == 2 }
        assertThat(februaryRecords).isEmpty()

        val juneRecords = dailyUsageRepository.findAll()
            .filter { it.user.email == "multimonth@example.com" }
            .filter { it.usageDate.monthValue == 6 }
        assertThat(juneRecords).isEmpty()

        // Test specific date queries across months
        val marchMidDate = LocalDate.of(2024, 3, 15)
        val aprilMidDate = LocalDate.of(2024, 4, 15)
        val mayMidDate = LocalDate.of(2024, 5, 15)
        val mayLateDate = LocalDate.of(2024, 5, 25) // Should have no data

        val marchMidUsage = dailyUsageRepository.findByUserAndUsageDate(user, marchMidDate)
        val aprilMidUsage = dailyUsageRepository.findByUserAndUsageDate(user, aprilMidDate)
        val mayMidUsage = dailyUsageRepository.findByUserAndUsageDate(user, mayMidDate)
        val mayLateUsage = dailyUsageRepository.findByUserAndUsageDate(user, mayLateDate)

        assertThat(marchMidUsage).hasSize(1)
        assertThat(aprilMidUsage).hasSize(1)
        assertThat(mayMidUsage).hasSize(1)
        assertThat(mayLateUsage).isEmpty() // No data after May 15th

        // Verify total records count
        val allUserRecords = dailyUsageRepository.findAll()
            .filter { it.user.email == "multimonth@example.com" }
        assertThat(allUserRecords).hasSize(76) // 31 + 30 + 15 = 76 total days

        // Test edge case: filtering by wrong email should return empty
        val wrongEmailRecords = dailyUsageRepository.findAll()
            .filter { it.user.email == "nonexistent@example.com" }
            .filter { it.usageDate in marchStart..mayEnd }
        assertThat(wrongEmailRecords).isEmpty()

        println("Multi-Month Usage Statistics:")
        println("March 2024 (Full): ${String.format("%.3f", marchUsageGB)} GB (${marchAverageDaily / (1024 * 1024)} MB/day avg)")
        println("April 2024 (Full): ${String.format("%.3f", aprilUsageGB)} GB (${aprilAverageDaily / (1024 * 1024)} MB/day avg)")
        println("May 2024 (Partial): ${String.format("%.3f", mayActualUsageGB)} GB actual, ${String.format("%.3f", mayProjectedUsageGB)} GB projected")
        println("Total Records: ${allUserRecords.size} days across 3 months")
        println("Usage Growth: ${marchAverageDaily / (1024 * 1024)} → ${aprilAverageDaily / (1024 * 1024)} → ${mayAverageDaily / (1024 * 1024)} MB/day")
    }

    private fun createUserWithDatabases(email: String, name: String, databaseNames: List<String>): User {
        val databases = databaseNames.map { Database(it) }.toMutableSet()

        val user = User(
            email = email,
            name = name,
            databases = databases
        )

        return userRepository.save(user)
    }

    private fun generateDailyUsage(user: User, date: LocalDate, userIndex: Int, dayOffset: Int): DailyUsage {
        // Generate realistic usage based on number of databases and usage patterns
        val baseBytesPerDb = 50_000_000L // 50 MB base per database
        val databaseCount = user.databases.size

        // Add some variation based on user index and day
        val userMultiplier = 1 + (userIndex * 0.5) // Different users have different base usage
        val dailyVariation = generateSinusoidalVariation(dayOffset) // Simulate weekly/monthly patterns
        val randomFactor = Random.nextDouble(0.8, 1.2) // ±20% random variation

        val totalBytes = (baseBytesPerDb * databaseCount * userMultiplier * dailyVariation * randomFactor).toLong()

        return DailyUsage(
            user = user,
            usageDate = date,
            totalBytes = totalBytes.coerceAtLeast(1000) // Minimum 1KB
        )
    }

    private fun generateSinusoidalVariation(dayOffset: Int): Double {
        // Create a sinusoidal pattern that simulates business cycles
        // Weekly pattern (7 days) + monthly pattern (30 days)
        val weeklyPattern = sin(2 * Math.PI * dayOffset / 7.0) * 0.2 + 1.0 // ±20% weekly variation
        val monthlyPattern = sin(2 * Math.PI * dayOffset / 30.0) * 0.3 + 1.0 // ±30% monthly variation

        return (weeklyPattern + monthlyPattern) / 2.0
    }

    private fun generateSinusoidalUsage(dayOffset: Int, baseBytes: Long): Long {
        val variation = generateSinusoidalVariation(dayOffset)
        return (baseBytes * variation).toLong()
    }
}
package com.nofrillsdb.jobs

import com.nofrillsdb.payment.PaymentManager
import com.nofrillsdb.payment.StripeService
import com.nofrillsdb.payment.db.Payment
import com.nofrillsdb.payment.repository.PaymentRepository
import com.nofrillsdb.provisioning.Database
import com.nofrillsdb.usage.db.DailyUsage
import com.nofrillsdb.usage.repository.DailyUsageRepository
import com.nofrillsdb.user.repository.UserRepository
import com.nofrillsdb.users.model.db.User
import com.stripe.model.Invoice
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

@SpringBootTest
@Testcontainers
class MonthlyBillingJobTest {

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

            // Provisioning datasource configuration
            registry.add("provisioning.datasource.url", postgres::getJdbcUrl)
            registry.add("provisioning.datasource.username", postgres::getUsername)
            registry.add("provisioning.datasource.password", postgres::getPassword)
            registry.add("provisioning.datasource.driver-class-name") { "org.postgresql.Driver" }

            // Provisioning configuration
            registry.add("provisioning.client-ca-key") { "dummyKeyForTesting" }
            registry.add("provisioning.connection-limit") { "100" }
            registry.add("provisioning.statement-timeout") { "30000" }
            registry.add("provisioning.pool-user") { "testuser" }

            registry.add("spring.task.scheduling.enabled") { "false" }

            // Disable Stripe in tests
            registry.add("stripe.secret-key") { "" }
        }
    }

    @Autowired
    private lateinit var monthlyBillingJob: MonthlyBillingJob

    @Autowired
    private lateinit var dailyUsageRepository: DailyUsageRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @MockitoBean
    private lateinit var stripeService: StripeService

    @MockitoBean
    private lateinit var paymentManager: PaymentManager

    @BeforeEach
    fun setUp() {
        dailyUsageRepository.deleteAll()
        paymentRepository.deleteAll()
        userRepository.deleteAll()
        reset(stripeService, paymentManager)
    }

    @Test
    fun `should process monthly billing for users with payment methods and sufficient usage`() {
        // Setup test data for previous month
        val previousMonth = YearMonth.now().minusMonths(1)
        val startDate = previousMonth.atDay(1)
        val endDate = previousMonth.atEndOfMonth()

        // Create users with different usage levels
        val user1 = createUser("user1@test.com", "User One")
        val user2 = createUser("user2@test.com", "User Two")
        val user3 = createUser("user3@test.com", "User Three")

        // Create payment records
        val payment1 = createPayment(user1, "cus_test1", "pm_test1")
        val payment2 = createPayment(user2, "cus_test2", "pm_test2")
        // user3 has no payment record at all

        // Create usage data for the previous month (amounts in GB converted to bytes)
        // New pricing: 100MB free tier, then $1/GB, minimum charge $5 if >200MB
        val usage1 = 0.25 * 1024 * 1024 * 1024 // 0.25 GB = 250MB = 150MB billable = $0.15, but minimum $5 since >200MB
        val usage2 = 5.5 * 1024 * 1024 * 1024 // 5.5 GB = 5.4GB billable = $5.40
        val usage3 = 25.0 * 1024 * 1024 * 1024 // 25 GB = 24.9GB billable = $24.90

        createDailyUsageRecords(user1, startDate, endDate, usage1.toLong())
        createDailyUsageRecords(user2, startDate, endDate, usage2.toLong())
        createDailyUsageRecords(user3, startDate, endDate, usage3.toLong())

        // Mock payment manager responses
        whenever(paymentManager.getPaymentByUser(user1)).thenReturn(payment1)
        whenever(paymentManager.getPaymentByUser(user2)).thenReturn(payment2)
        whenever(paymentManager.getPaymentByUser(user3)).thenReturn(null)

        // Mock Stripe invoice creation
        val mockInvoice1 = mock<Invoice> { on { id } doReturn "inv_test1" }
        val mockInvoice2 = mock<Invoice> { on { id } doReturn "inv_test2" }

        whenever(stripeService.createDraftInvoice(
            eq("cus_test1"), any(), eq(500L), eq("usd"), any(), any()
        )).thenReturn(mockInvoice1)
        whenever(stripeService.createDraftInvoice(
            eq("cus_test2"), any(), eq(540L), eq("usd"), any(), any()
        )).thenReturn(mockInvoice2)

        // Run the billing job
        monthlyBillingJob.runBillingJobManually(previousMonth)

        // Verify that user1 and user2 get invoices (user3 has no payment method)
        verify(stripeService, times(1)).createDraftInvoice(
            eq("cus_test1"), any(), eq(500L), eq("usd"), any(), any()
        )
        verify(stripeService, times(1)).createDraftInvoice(
            eq("cus_test2"), any(), eq(540L), eq("usd"), any(), any()
        )
        verify(stripeService, never()).createDraftInvoice(
            eq("cus_test3"), any(), any(), any(), any(), any()
        )

        // Verify payment manager was called for all users
        verify(paymentManager, times(1)).getPaymentByUser(user1)
        verify(paymentManager, times(1)).getPaymentByUser(user2)
        verify(paymentManager, times(1)).getPaymentByUser(user3)
    }

    @Test
    fun `should skip users with no payment methods`() {
        val previousMonth = YearMonth.now().minusMonths(1)
        val startDate = previousMonth.atDay(1)
        val endDate = previousMonth.atEndOfMonth()

        val user = createUser("nopayment@test.com", "No Payment User")
        val usage = 20.0 * 1024 * 1024 * 1024 // 20 GB = 19.9GB billable = $19.90

        createDailyUsageRecords(user, startDate, endDate, usage.toLong())

        // Mock no payment method
        whenever(paymentManager.getPaymentByUser(user)).thenReturn(null)

        monthlyBillingJob.runBillingJobManually(previousMonth)

        verify(paymentManager, times(1)).getPaymentByUser(user)
        verify(stripeService, never()).createDraftInvoice(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `should skip users with usage within free tier`() {
        val previousMonth = YearMonth.now().minusMonths(1)
        val startDate = previousMonth.atDay(1)
        val endDate = previousMonth.atEndOfMonth()

        val user = createUser("freetier@test.com", "Free Tier User")
        val payment = createPayment(user, "cus_free", "pm_free")

        // Create usage within free tier (50 MB)
        val freeTierUsage = 50L * 1024 * 1024
        createDailyUsageRecords(user, startDate, endDate, freeTierUsage)

        whenever(paymentManager.getPaymentByUser(user)).thenReturn(payment)

        monthlyBillingJob.runBillingJobManually(previousMonth)

        verify(paymentManager, times(1)).getPaymentByUser(user)
        verify(stripeService, never()).createDraftInvoice(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `should skip users with usage below minimum charge threshold`() {
        val previousMonth = YearMonth.now().minusMonths(1)
        val startDate = previousMonth.atDay(1)
        val endDate = previousMonth.atEndOfMonth()

        val user = createUser("lowusage@test.com", "Low Usage User")
        val payment = createPayment(user, "cus_low", "pm_low")

        // Create usage below 200MB threshold (150MB = within minimum charge criteria)
        val lowUsage = 150L * 1024 * 1024 // 150MB total
        createDailyUsageRecords(user, startDate, endDate, lowUsage.toLong())

        whenever(paymentManager.getPaymentByUser(user)).thenReturn(payment)

        monthlyBillingJob.runBillingJobManually(previousMonth)

        verify(paymentManager, times(1)).getPaymentByUser(user)
        verify(stripeService, never()).createDraftInvoice(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `should handle users with no usage data`() {
        val previousMonth = YearMonth.now().minusMonths(1)

        val user = createUser("nousage@test.com", "No Usage User")
        val payment = createPayment(user, "cus_nousage", "pm_nousage")

        // No usage records created

        whenever(paymentManager.getPaymentByUser(user)).thenReturn(payment)

        monthlyBillingJob.runBillingJobManually(previousMonth)

        // Should not try to create invoice for users with no usage
        verify(stripeService, never()).createDraftInvoice(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `should calculate correct invoice amounts for different usage levels`() {
        val previousMonth = YearMonth.now().minusMonths(1)
        val startDate = previousMonth.atDay(1)
        val endDate = previousMonth.atEndOfMonth()

        val testCases = listOf(
            Triple("user1@test.com", 0.25, 500L), // 0.25 GB = 250MB = 150MB billable = $0.15, but minimum $5 since >200MB
            Triple("user2@test.com", 25.6, 2550L), // 25.6 GB = 25.5GB billable = $25.50 = 2550 cents
            Triple("user3@test.com", 100.75, 10065L) // 100.75 GB = 100.65GB billable = $100.65 = 10065 cents
        )

        testCases.forEachIndexed { index, (email, gb, expectedCents) ->
            val user = createUser(email, "Test User ${index + 1}")
            val payment = createPayment(user, "cus_$index", "pm_$index")
            val usage = (gb * 1024 * 1024 * 1024).toLong()

            createDailyUsageRecords(user, startDate, endDate, usage)
            whenever(paymentManager.getPaymentByUser(user)).thenReturn(payment)

            val mockInvoice = mock<Invoice> { on { id } doReturn "inv_$index" }
            whenever(stripeService.createDraftInvoice(
                eq("cus_$index"), any(), eq(expectedCents), eq("usd"), any(), any()
            )).thenReturn(mockInvoice)
        }

        monthlyBillingJob.runBillingJobManually(previousMonth)

        // Verify each invoice was created with correct amount
        testCases.forEachIndexed { index, (_, _, expectedCents) ->
            verify(stripeService, times(1)).createDraftInvoice(
                eq("cus_$index"), any(), eq(expectedCents), eq("usd"), any(), any()
            )
        }
    }

    @Test
    fun `should handle Stripe API failures gracefully`() {
        val previousMonth = YearMonth.now().minusMonths(1)
        val startDate = previousMonth.atDay(1)
        val endDate = previousMonth.atEndOfMonth()

        val user1 = createUser("user1@test.com", "User One")
        val user2 = createUser("user2@test.com", "User Two")

        val payment1 = createPayment(user1, "cus_fail", "pm_fail")
        val payment2 = createPayment(user2, "cus_success", "pm_success")

        val usage = 15.0 * 1024 * 1024 * 1024 // 15 GB = 14.9GB billable = $14.90
        createDailyUsageRecords(user1, startDate, endDate, usage.toLong())
        createDailyUsageRecords(user2, startDate, endDate, usage.toLong())

        whenever(paymentManager.getPaymentByUser(user1)).thenReturn(payment1)
        whenever(paymentManager.getPaymentByUser(user2)).thenReturn(payment2)

        // Mock Stripe failure for first user, success for second
        whenever(stripeService.createDraftInvoice(
            eq("cus_fail"), any(), eq(1490L), eq("usd"), any(), any()
        )).thenThrow(RuntimeException("Stripe API Error"))

        val mockInvoice = mock<Invoice> { on { id } doReturn "inv_success" }
        whenever(stripeService.createDraftInvoice(
            eq("cus_success"), any(), eq(1490L), eq("usd"), any(), any()
        )).thenReturn(mockInvoice)

        // Should not throw exception and continue processing
        monthlyBillingJob.runBillingJobManually(previousMonth)

        // Verify both attempts were made
        verify(stripeService, times(1)).createDraftInvoice(
            eq("cus_fail"), any(), eq(1490L), eq("usd"), any(), any()
        )
        verify(stripeService, times(1)).createDraftInvoice(
            eq("cus_success"), any(), eq(1490L), eq("usd"), any(), any()
        )
    }

    @Test
    fun `should create invoice with correct description and period`() {
        val previousMonth = YearMonth.now().minusMonths(1)
        val startDate = previousMonth.atDay(1)
        val endDate = previousMonth.atEndOfMonth()

        val user = createUser("test@test.com", "Test User")
        val payment = createPayment(user, "cus_test", "pm_test")
        val usage = 15.0 * 1024 * 1024 * 1024 // 15 GB

        createDailyUsageRecords(user, startDate, endDate, usage.toLong())
        whenever(paymentManager.getPaymentByUser(user)).thenReturn(payment)

        val mockInvoice = mock<Invoice> { on { id } doReturn "inv_test" }
        whenever(stripeService.createDraftInvoice(any(), any(), any(), any(), any(), any()))
            .thenReturn(mockInvoice)

        monthlyBillingJob.runBillingJobManually(previousMonth)

        val expectedDescription = "Database storage usage for ${previousMonth.month.name.lowercase().replaceFirstChar { it.uppercaseChar() }} ${previousMonth.year} - 15.00 GB"

        verify(stripeService, times(1)).createDraftInvoice(
            customerId = eq("cus_test"),
            description = eq(expectedDescription),
            amount = eq(1490L),
            currency = eq("usd"),
            periodStart = eq(startDate.atStartOfDay().toInstant(java.time.ZoneOffset.UTC).epochSecond),
            periodEnd = eq(endDate.atTime(23, 59, 59).toInstant(java.time.ZoneOffset.UTC).epochSecond)
        )
    }

    @Test
    fun `should process only users with usage in specified month`() {
        val currentMonth = YearMonth.now()
        val previousMonth = currentMonth.minusMonths(1)
        val twoMonthsAgo = currentMonth.minusMonths(2)

        val user1 = createUser("user1@test.com", "User One")
        val user2 = createUser("user2@test.com", "User Two")

        val payment1 = createPayment(user1, "cus_1", "pm_1")
        val payment2 = createPayment(user2, "cus_2", "pm_2")

        // User1 has usage in previous month, User2 has usage two months ago
        val usage = 15.0 * 1024 * 1024 * 1024 // 15 GB = 14.9GB billable = $14.90
        createDailyUsageRecords(user1, previousMonth.atDay(1), previousMonth.atEndOfMonth(), usage.toLong())
        createDailyUsageRecords(user2, twoMonthsAgo.atDay(1), twoMonthsAgo.atEndOfMonth(), usage.toLong())

        whenever(paymentManager.getPaymentByUser(user1)).thenReturn(payment1)
        whenever(paymentManager.getPaymentByUser(user2)).thenReturn(payment2)

        val mockInvoice = mock<Invoice> { on { id } doReturn "inv_1" }
        whenever(stripeService.createDraftInvoice(any(), any(), any(), any(), any(), any()))
            .thenReturn(mockInvoice)

        // Process previous month only
        monthlyBillingJob.runBillingJobManually(previousMonth)

        // Only user1 should be processed
        verify(stripeService, times(1)).createDraftInvoice(
            eq("cus_1"), any(), eq(1490L), eq("usd"), any(), any()
        )
        verify(stripeService, never()).createDraftInvoice(
            eq("cus_2"), any(), any(), any(), any(), any()
        )
    }

    /* ============================================================
     * Helper Methods
     * ============================================================ */

    private fun createUser(email: String, name: String): User {
        val databases = mutableSetOf(Database("test_db"))
        val user = User(email = email, name = name, databases = databases)
        return userRepository.save(user)
    }

    private fun createPayment(user: User, stripeCustomerId: String, defaultPaymentMethodId: String?): Payment {
        val payment = Payment(
            user = user,
            stripeCustomerId = stripeCustomerId,
            defaultPaymentMethodId = defaultPaymentMethodId,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        return paymentRepository.save(payment)
    }

    private fun createDailyUsageRecords(user: User, startDate: LocalDate, endDate: LocalDate, totalBytes: Long) {
        val daysInMonth = endDate.dayOfMonth
        val bytesPerDay = totalBytes / daysInMonth

        var currentDate = startDate
        while (!currentDate.isAfter(endDate)) {
            val remainingDays = endDate.dayOfMonth - currentDate.dayOfMonth + 1
            val bytesForThisDay = if (remainingDays == 1) {
                // Last day gets remaining bytes to ensure exact total
                totalBytes - (bytesPerDay * (currentDate.dayOfMonth - 1))
            } else {
                bytesPerDay
            }

            val dailyUsage = DailyUsage(
                user = user,
                usageDate = currentDate,
                totalBytes = bytesForThisDay
            )
            dailyUsageRepository.save(dailyUsage)
            currentDate = currentDate.plusDays(1)
        }
    }
}
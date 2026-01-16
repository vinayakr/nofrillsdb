package com.nofrillsdb.jobs

import com.nofrillsdb.payment.PaymentManager
import com.nofrillsdb.payment.StripeService
import com.nofrillsdb.usage.repository.DailyUsageRepository
import com.nofrillsdb.users.model.db.User
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Component
class MonthlyBillingJob(
    private val dailyUsageRepository: DailyUsageRepository,
    private val paymentManager: PaymentManager,
    private val stripeService: StripeService
) {

    companion object {
        private val logger = LoggerFactory.getLogger(MonthlyBillingJob::class.java)
        private const val BILLING_RATE_PER_GB = 1.0 // $1.00 per GB
        private const val BYTES_PER_GB = 1024L * 1024L * 1024L
        private const val BYTES_PER_MB = 1024L * 1024L
        private const val FREE_TIER_MB = 100L // 100MB free tier
        private const val MIN_CHARGE_CENTS = 500L // $5.00 minimum charge
    }

    @Scheduled(cron = "0 5 1 1 * ?") // Run at 1:05 AM on the 1st day of every month
    fun processMonthlyBilling() {
        logger.info("Starting monthly billing job...")

        try {
            val previousMonth = YearMonth.now().minusMonths(1)
            val startDate = previousMonth.atDay(1)
            val endDate = previousMonth.atEndOfMonth()

            logger.info("Processing billing for period: {} to {}", startDate, endDate)

            val monthlyUsageData = dailyUsageRepository.findMonthlyUsageByDateRange(startDate, endDate)

            var processedCount = 0
            var skippedCount = 0

            for (usageEntry in monthlyUsageData) {
                try {
                    val user = usageEntry[0] as User
                    val totalBytes = usageEntry[1] as Long

                    val result = processUserBilling(user, totalBytes, startDate, endDate)

                    if (result) {
                        processedCount++
                        logger.debug("Created invoice for user: {} ({})", user.email, user.id)
                    } else {
                        skippedCount++
                        logger.debug("Skipped billing for user: {} ({})", user.email, user.id)
                    }

                } catch (e: Exception) {
                    logger.error("Failed to process billing for user entry: {}", usageEntry, e)
                }
            }

            logger.info("Monthly billing job completed. Processed: {}, Skipped: {}", processedCount, skippedCount)

        } catch (e: Exception) {
            logger.error("Monthly billing job failed", e)
        }
    }

    private fun processUserBilling(
        user: User,
        totalBytes: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): Boolean {
        try {
            // Get user's payment info
            val payment = paymentManager.getPaymentByUser(user)
            if (payment == null) {
                logger.info("No payment method found for user: {} ({}). Skipping billing.", user.email, user.id)
                return false
            }

            // Calculate billing amount with free tier
            val chargeAmount = calculateChargeAmount(totalBytes)
            val totalGB = totalBytes.toDouble() / BYTES_PER_GB
            val totalMB = totalBytes.toDouble() / BYTES_PER_MB

            if (chargeAmount == 0L) {
                logger.info("Usage for user {} ({}) is within free tier. Usage: %.2f MB",
                    user.email, user.id, totalMB)
                return false
            }

            if (chargeAmount < MIN_CHARGE_CENTS) {
                logger.info("Usage for user {} ({}) is below minimum charge threshold. Usage: %.2f GB, Calculated charge: $%.2f",
                    user.email, user.id, totalGB, chargeAmount / 100.0)
                return false
            }

            // Create invoice description
            val description = "Database storage usage for ${startDate.format(DateTimeFormatter.ofPattern("MMMM yyyy"))} - ${String.format("%.2f", totalGB)} GB"

            // Convert dates to Unix timestamps for Stripe
            val periodStart = startDate.atStartOfDay().toInstant(ZoneOffset.UTC).epochSecond
            val periodEnd = endDate.atTime(23, 59, 59).toInstant(ZoneOffset.UTC).epochSecond

            // Create draft invoice in Stripe
            val invoice = stripeService.createDraftInvoice(
                customerId = payment.stripeCustomerId,
                description = description,
                amount = chargeAmount,
                currency = "usd",
                periodStart = periodStart,
                periodEnd = periodEnd
            )

            logger.info("Created draft invoice {} for user {} ({}) - Amount: {} USD, Usage: %.2f GB",
                invoice.id, user.email, user.id, chargeAmount / 100.0, totalGB)

            return true

        } catch (e: Exception) {
            logger.error("Failed to process billing for user {} ({})", user.email, user.id, e)
            return false
        }
    }

    private fun calculateChargeAmount(totalBytes: Long): Long {
        // Calculate free tier
        val freeTierBytes = FREE_TIER_MB * BYTES_PER_MB

        // If usage is within free tier, no charge
        if (totalBytes <= freeTierBytes) {
            return 0L
        }

        // Calculate billable bytes (usage beyond free tier)
        val billableBytes = totalBytes - freeTierBytes
        val billableGB = billableBytes.toDouble() / BYTES_PER_GB

        // Calculate charge in dollars, then convert to cents
        val chargeInDollars = billableGB * BILLING_RATE_PER_GB
        val chargeInCents = (chargeInDollars * 100).toLong()

        // Apply minimum charge of $5 if usage is over 200MB (100MB free + 100MB additional)
        val minimumChargeThresholdBytes = 200L * BYTES_PER_MB
        if (totalBytes > minimumChargeThresholdBytes && chargeInCents < MIN_CHARGE_CENTS) {
            return MIN_CHARGE_CENTS
        }

        return chargeInCents
    }

    // Manual trigger method for testing
    fun runBillingJobManually(targetMonth: YearMonth? = null) {
        logger.info("Manually triggering billing job...")

        val monthToProcess = targetMonth ?: YearMonth.now().minusMonths(1)
        val startDate = monthToProcess.atDay(1)
        val endDate = monthToProcess.atEndOfMonth()

        logger.info("Processing billing for period: {} to {}", startDate, endDate)

        val monthlyUsageData = dailyUsageRepository.findMonthlyUsageByDateRange(startDate, endDate)

        for (usageEntry in monthlyUsageData) {
            val user = usageEntry[0] as User
            val totalBytes = usageEntry[1] as Long

            processUserBilling(user, totalBytes, startDate, endDate)
        }

        logger.info("Manual billing job completed for month: {}", monthToProcess)
    }
}
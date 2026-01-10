package com.nofrillsdb.jobs

import org.springframework.context.annotation.Profile
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Profile("dev")
@RestController
@RequestMapping("/api/admin/billing")
class BillingJobController(
    private val monthlyBillingJob: MonthlyBillingJob
) {

    @PostMapping("/run-monthly")
    fun runMonthlyBilling(@RequestParam(required = false) month: String?): BillingJobResponse {
        return try {
            val targetMonth = month?.let { YearMonth.parse(it, DateTimeFormatter.ofPattern("yyyy-MM")) }

            monthlyBillingJob.runBillingJobManually(targetMonth)

            BillingJobResponse(
                success = true,
                message = "Monthly billing job completed successfully for month: ${targetMonth ?: "previous month"}",
                processedMonth = targetMonth?.toString() ?: YearMonth.now().minusMonths(1).toString()
            )
        } catch (e: Exception) {
            BillingJobResponse(
                success = false,
                message = "Monthly billing job failed: ${e.message}",
                error = e::class.simpleName
            )
        }
    }
}

data class BillingJobResponse(
    val success: Boolean,
    val message: String,
    val processedMonth: String? = null,
    val error: String? = null
)
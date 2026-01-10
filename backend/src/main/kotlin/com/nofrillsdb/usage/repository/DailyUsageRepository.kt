package com.nofrillsdb.usage.repository

import com.nofrillsdb.usage.db.DailyUsage
import com.nofrillsdb.users.model.db.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface DailyUsageRepository : JpaRepository<DailyUsage, Long> {
    fun findByUserAndUsageDate(user: User, usageDate: LocalDate): List<DailyUsage>

    @Query("SELECT du.user, SUM(du.totalBytes) FROM DailyUsage du WHERE du.usageDate >= :startDate AND du.usageDate <= :endDate GROUP BY du.user")
    fun findMonthlyUsageByDateRange(@Param("startDate") startDate: LocalDate, @Param("endDate") endDate: LocalDate): List<Array<Any>>

    @Query("SELECT SUM(du.totalBytes) FROM DailyUsage du WHERE du.user = :user AND du.usageDate >= :startDate AND du.usageDate <= :endDate")
    fun findTotalUsageByUserAndDateRange(@Param("user") user: User, @Param("startDate") startDate: LocalDate, @Param("endDate") endDate: LocalDate): Long?
}
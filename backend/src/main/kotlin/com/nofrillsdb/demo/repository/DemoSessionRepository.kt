package com.nofrillsdb.demo.repository

import com.nofrillsdb.demo.db.DemoSession
import org.springframework.data.jpa.repository.JpaRepository

interface DemoSessionRepository : JpaRepository<DemoSession, String> {
    fun findByCleanedUpFalse(): List<DemoSession>
    fun countByCleanedUpFalse(): Long
}

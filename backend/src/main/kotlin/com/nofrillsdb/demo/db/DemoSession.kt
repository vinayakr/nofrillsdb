package com.nofrillsdb.demo.db

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "demo_sessions")
data class DemoSession(
    @Id
    val token: String,

    @Column(nullable = false)
    val role: String,

    @Column(name = "database_name", nullable = false)
    val databaseName: String,

    @Column(nullable = false)
    val password: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "storage_exceeded", nullable = false)
    var storageExceeded: Boolean = false,

    @Column(name = "cleaned_up", nullable = false)
    var cleanedUp: Boolean = false,
)

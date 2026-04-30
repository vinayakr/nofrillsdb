package com.nofrillsdb.demo

data class CreateDemoResponse(
    val token: String,
    val role: String,
    val password: String,
    val databaseName: String,
)

data class DemoStatusResponse(
    val token: String,
    val role: String,
    val password: String,
    val databaseName: String,
    val storageExceeded: Boolean,
    val sizeBytes: Long,
    val storageLimitBytes: Long,
)

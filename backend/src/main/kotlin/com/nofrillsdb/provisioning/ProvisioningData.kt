package com.nofrillsdb.provisioning

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateDBRequest(
    @field:NotBlank
    @field:Size(min=3, max=40)
    @field:Pattern(regexp = "^[a-zA-Z_][a-z0-9_]+$")
    val name: String,
)

data class CreateDBResponse(
    val databaseName: String
)

data class IssuedClientCredential(
    val role: String,
    val privateKeyPem: String,
    val certificatePem: String,
    val serialHex: String,
    val fingerprintSha256Hex: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
)

package com.nofrillsdb.provisioning

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class CreateDBRequest(
    @field:NotBlank
    @field:Size(min=3, max=40)
    @field:Pattern(regexp = "^[a-zA-Z_][a-z0-9_]+$")
    val name: String,
    @field:Size(min=12, max=63)
    val password: String
)

data class CreateDBResponse(
    val databaseName: String,
    val username: String,
)
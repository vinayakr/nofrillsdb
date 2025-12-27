package com.nofrillsdb.user

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UserCreateRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email should be valid")
    val email: String,

    @field:Size(max = 1000, message = "Name cannot exceed 1000 characters")
    val name: String? = null,
)

data class UserUpdateRequest(
    @field:Size(max = 1000, message = "Name cannot exceed 100 characters")
    val name: String? = null
)

data class UserUpdateResponse(
    val id: Long?,
    val email: String,
    val name: String?,
    val needsTokenRefresh: Boolean = false)

package com.nofrillsdb.registration

data class RegistrationStatusResponse(
    val email: String?,
    val registered: Boolean,
    val hasUserIdInToken: Boolean,
    val needsRelogin: Boolean
)

data class UserProfileResponse(
    val id: Long?,
    val email: String,
    val name: String?
)

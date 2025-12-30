package com.nofrillsdb.user

import com.nofrillsdb.auth.Auth0ManagementService
import com.nofrillsdb.user.exception.UnauthorizedException
import com.nofrillsdb.registration.RegistrationStatusResponse
import com.nofrillsdb.registration.UserProfileResponse
import com.nofrillsdb.utils.Auth0TokenValidator
import com.nofrillsdb.utils.JWTUtils
import com.nofrillsdb.utils.JWTUtils.Companion.extractToken
import com.nofrillsdb.user.exception.UserNotFoundException
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/user")
class UserController(
    val auth0TokenValidator: Auth0TokenValidator,
    val userManager: UserManager,
    val auth0ManagementService: Auth0ManagementService
) {

    @PostMapping("/register")
    fun registerUser(
        @Valid @RequestBody request: UserCreateRequest,
        @RequestHeader("Authorization") authHeader: String
    ): UserUpdateResponse {
        val token = extractToken(authHeader)
            ?: throw UnauthorizedException("Authorization token is required")

        val validatedJwt = auth0TokenValidator.validateAuth0Token(token)

        val registerResponse = userManager.registerUser(request, validatedJwt)
        val savedUser = registerResponse.first

        return UserUpdateResponse(
            id = savedUser.id,
            email = savedUser.email,
            name = savedUser.name,
            needsTokenRefresh = true
        )
    }

    @GetMapping("/registration-status")
    fun checkRegistrationStatus(@AuthenticationPrincipal jwt: Jwt): RegistrationStatusResponse {
        val hasUserId = auth0TokenValidator.hasUserIdClaim(jwt)

        if (hasUserId) {
            val userId= JWTUtils.getUserId(jwt);
            if (userManager.doesUserExist(userId)) {
                val existingUser = userManager.getUserById(userId)
                return RegistrationStatusResponse(
                    email = existingUser.email,
                    registered = true,
                    hasUserIdInToken = true,
                    needsRelogin = false
                )
            } else {
                return RegistrationStatusResponse(
                    email = null,
                    registered = false,
                    hasUserIdInToken = true,
                    needsRelogin = false
                )
            }
        } else {
            return RegistrationStatusResponse(
                email = null,
                registered = false,
                hasUserIdInToken = false,
                needsRelogin = true
            )
        }
    }

    @GetMapping("/profile")
    fun getCurrentUserProfile(@AuthenticationPrincipal jwt: Jwt): UserProfileResponse {
        val userId= JWTUtils.getUserId(jwt);
        if (userManager.doesUserExist(userId)) {
            val existingUser = userManager.getUserById(userId)
            return UserProfileResponse(
                id = existingUser.id,
                email = existingUser.email,
                name = existingUser.name
            )
        } else {
            throw UserNotFoundException("User not found. Please complete registration first.")
        }
    }

    @PutMapping("/profile")
    fun updateCurrentUserProfile(
        @Valid @RequestBody request: UserUpdateRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): UserProfileResponse {
        val userId= JWTUtils.getUserId(jwt);

        if (userManager.doesUserExist(userId)) {
            val existingUser = userManager.getUserById(userId)
            val updatedUser = existingUser.copy(
                name = request.name ?: existingUser.name
            )
            val savedUser = userManager.saveUser(updatedUser)

            return UserProfileResponse(
                id = savedUser.id,
                email = savedUser.email,
                name = savedUser.name
            )
        } else {
            throw UserNotFoundException("User not found. Please complete registration first.")
        }
    }

    @GetMapping("/debug/auth0-config")
    fun debugAuth0Config(): Map<String, Any> {
        return mapOf(
            "isConfigured" to auth0ManagementService.isConfigured()
        )
    }

}

data class EmailRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email should be valid")
    val email: String
)
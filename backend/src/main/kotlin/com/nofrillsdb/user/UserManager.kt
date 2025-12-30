package com.nofrillsdb.user

import com.nofrillsdb.auth.Auth0ManagementService
import com.nofrillsdb.user.exception.UserAlreadyExistsException
import com.nofrillsdb.user.repository.UserRepository
import com.nofrillsdb.users.model.db.User
import com.nofrillsdb.utils.Auth0TokenValidator
import com.nofrillsdb.user.exception.UserNotFoundException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service

@Service
class UserManager(
    val userRepository: UserRepository,
    val auth0TokenValidator: Auth0TokenValidator,
    val auth0ManagementService: Auth0ManagementService
) {
    fun registerUser(request: UserCreateRequest, validatedJwt: Jwt): Pair<User, String> {
        if (doesUserExist(request.email)) throw UserAlreadyExistsException("User with this email already exists")

        val user = User(
            email = request.email,
            name = request.name
        )

        val savedUser = userRepository.save(user)

        val metadataUpdated = syncUserMetadataIfNeeded(validatedJwt, savedUser)

        val responseMessage = if (metadataUpdated) {
            "User registered successfully. Please refresh your token to access all features."
        } else {
            "User registered successfully. Please log out and log back in to complete setup."
        }

        return Pair(savedUser, responseMessage)
    }

    fun syncUserMetadataIfNeeded(validatedJwt: Jwt, user: User): Boolean {
        return try {
            // Only sync if user doesn't already have userId in token
            if (!auth0TokenValidator.hasUserIdClaim(validatedJwt)) {
                if (auth0ManagementService.isConfigured()) {
                    val auth0Sub = auth0TokenValidator.extractSubFromToken(validatedJwt)
                    auth0ManagementService.updateUserMetadata(auth0Sub, user.id!!)
                    true
                } else {
                    false
                }
            } else {
                true
            }
        } catch (e: Exception) {
            println("Warning: Failed to update Auth0 metadata for user ${user.email}: ${e.message}")
            false
        }
    }

    fun saveUser(user: User): User {
        return userRepository.save(user);
    }
    fun doesUserExist(userId: Long): Boolean {
        return userRepository.existsById(userId);
    }

    fun doesUserExist(email: String): Boolean {
        return userRepository.existsByEmail(email);
    }
    fun getUserById(userId: Long): User {
        return userRepository.findById(userId).orElseThrow {
            UserNotFoundException("User with ID $userId not found")
        }
    }

    fun getUserByEmail(email: String): User {
        return userRepository.findByEmail(email)
            ?: throw UserNotFoundException("User with email $email not found")
    }
}
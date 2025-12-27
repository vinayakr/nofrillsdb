package com.nofrillsdb.utils

import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class Auth0TokenValidator {

    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private lateinit var issuerUri: String

    private val jwtDecoder: JwtDecoder by lazy {
        JwtDecoders.fromIssuerLocation(issuerUri)
    }

    fun validateAuth0Token(token: String): Jwt {
        try {
            val jwt = jwtDecoder.decode(token)

            // Additional validation checks
            if (jwt.expiresAt?.isBefore(Instant.now()) == true) {
                throw SecurityException("Token has expired")
            }

            if (jwt.audience?.isEmpty() != false) {
                throw SecurityException("Token has no audience")
            }

            return jwt
        } catch (e: Exception) {
            throw SecurityException("Invalid Auth0 token: ${e.message}")
        }
    }

    fun extractSubFromToken(jwt: Jwt): String {
        return jwt.subject
            ?: throw SecurityException("Subject not found in token")
    }

    fun hasUserIdClaim(jwt: Jwt): Boolean {
        return jwt.hasClaim("userId") && jwt.getClaimAsString("userId")?.isNotBlank() == true
    }

}
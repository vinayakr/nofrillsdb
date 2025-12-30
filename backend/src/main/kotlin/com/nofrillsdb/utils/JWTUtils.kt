package com.nofrillsdb.utils

import org.springframework.security.oauth2.jwt.Jwt

class JWTUtils {
    companion object {
        fun getUserId(jwt: Jwt): Long {
            return jwt.getClaimAsString("userId")?.toLong()
                ?: throw IllegalStateException("User ID not found in JWT")
        }

        fun extractToken(authHeader: String): String? {
            val token = authHeader.removePrefix("Bearer ").trim()
            if (token.isBlank()) {
                return null
            }
            return token
        }
    }
}
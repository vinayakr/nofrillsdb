package com.nofrillsdb.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.time.Instant

@Service
class Auth0ManagementService(
    private val restClient: RestClient
) {

    @Value("\${auth0.management.domain}")
    private lateinit var auth0Domain: String

    @Value("\${auth0.management.client-id}")
    private lateinit var managementClientId: String

    @Value("\${auth0.management.client-secret}")
    private lateinit var managementClientSecret: String

    private var accessToken: String? = null
    private var tokenExpiresAt: Instant = Instant.MIN

    fun updateUserMetadata(auth0UserId: String, userId: Long) {
        val token = getAccessToken()

        val body = mapOf(
            "user_metadata" to mapOf("userId" to userId)
        )

        restClient.patch()
            .uri("https://$auth0Domain/api/v2/users/$auth0UserId")
            .header("Authorization", "Bearer $token")
            .body(body)
            .retrieve()
            .toBodilessEntity()
    }

    private fun getAccessToken(): String {
        if (accessToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return accessToken!!
        }

        val body = mapOf(
            "client_id" to managementClientId,
            "client_secret" to managementClientSecret,
            "audience" to "https://$auth0Domain/api/v2/",
            "grant_type" to "client_credentials",
            "scope" to "update:users read:users"
        )

        val response = restClient.post()
            .uri("https://$auth0Domain/oauth/token")
            .body(body)
            .retrieve()
            .body(Auth0TokenResponse::class.java)

        accessToken = response?.accessToken
        tokenExpiresAt = Instant.now().plusSeconds(response?.expiresIn?.minus(300) ?: 60000)

        return accessToken!!
    }

    fun isConfigured(): Boolean =
        auth0Domain.isNotBlank() &&
                managementClientId.isNotBlank() &&
                managementClientSecret.isNotBlank()
}

data class Auth0TokenResponse(
    val access_token: String,
    val expires_in: Long
) {
    val accessToken get() = access_token
    val expiresIn get() = expires_in
}
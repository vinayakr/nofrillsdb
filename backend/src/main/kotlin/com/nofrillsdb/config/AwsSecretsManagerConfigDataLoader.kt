package com.nofrillsdb.config

import org.springframework.boot.context.config.ConfigData
import org.springframework.boot.context.config.ConfigDataLoader
import org.springframework.boot.context.config.ConfigDataLoaderContext
import org.springframework.core.env.MapPropertySource
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

class AwsSecretsManagerConfigDataLoader: ConfigDataLoader<AwsSecretsManagerConfigDataResource> {
    override fun load(
        context: ConfigDataLoaderContext,
        resource: AwsSecretsManagerConfigDataResource
    ): ConfigData? {
        return ConfigData(listOf(MapPropertySource("aws-secretsmanager-" + resource.secretName, retrieveSecret(resource.secretName))))
    }

    private fun retrieveSecret(secretName: String?): Map<String, String> {
        if (secretName.isNullOrBlank()) return emptyMap()

        return SecretsManagerClient.builder()
            .region(Region.US_WEST_2)
            .build().use { secretsManager ->
                try {
                    val getSecretValueRequest = GetSecretValueRequest.builder()
                        .secretId(secretName)
                        .build()

                    val response = secretsManager.getSecretValue(getSecretValueRequest)
                    val secretJson = response.secretString()

                    parseSecret(secretJson)
                } catch (e: Exception) {
                    emptyMap()
                }
            }
    }

    private fun parseSecret(secretJson: String?): Map<String, String> {
        if (secretJson == null) return emptyMap()
        val mapper = ObjectMapper()
        return mapper.readValue(secretJson, object : TypeReference<Map<String, String>>() {})
    }

}
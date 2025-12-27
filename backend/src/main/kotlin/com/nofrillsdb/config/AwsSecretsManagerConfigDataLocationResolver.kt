package com.nofrillsdb.config

import org.springframework.boot.context.config.ConfigDataLocation
import org.springframework.boot.context.config.ConfigDataLocationNotFoundException
import org.springframework.boot.context.config.ConfigDataLocationResolver
import org.springframework.boot.context.config.ConfigDataLocationResolverContext

class AwsSecretsManagerConfigDataLocationResolver: ConfigDataLocationResolver<AwsSecretsManagerConfigDataResource> {
    companion object {
        val PREFIX = "aws-secretsmanager"
    }
    override fun isResolvable(
        context: ConfigDataLocationResolverContext,
        location: ConfigDataLocation
    ): Boolean {
        return location?.hasPrefix(PREFIX) ?: false
    }

    override fun resolve(
        context: ConfigDataLocationResolverContext,
        location: ConfigDataLocation
    ): List<AwsSecretsManagerConfigDataResource> {
        return resolveProfileSpecific(location)
    }

    fun resolveProfileSpecific(
        location: ConfigDataLocation) : List<AwsSecretsManagerConfigDataResource> {
        var locationValue = location.value
        var secretName = ""
        var optional = false
        if(locationValue.startsWith("optional:" + PREFIX + ":")) {
            optional = true
            secretName = locationValue.substring(("optional:" + PREFIX + ":").length)
        } else if (locationValue.startsWith(PREFIX + ":")) {
            secretName = locationValue.substring((PREFIX + ":").length)
        }
        else {
            throw ConfigDataLocationNotFoundException(location)
        }

        return listOf(AwsSecretsManagerConfigDataResource(secretName, optional))
    }
}
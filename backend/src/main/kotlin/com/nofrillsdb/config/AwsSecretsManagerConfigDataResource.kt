package com.nofrillsdb.config

import org.springframework.boot.context.config.ConfigDataResource

class AwsSecretsManagerConfigDataResource(val secretName:String, val optional: Boolean): ConfigDataResource() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AwsSecretsManagerConfigDataResource

        if (optional != other.optional) return false
        if (secretName != other.secretName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = optional.hashCode()
        result = 31 * result + secretName.hashCode()
        return result
    }

    override fun toString(): String {
        return "AwsSecretsManagerConfigDataResource(secretName='$secretName', optional=$optional)"
    }


}
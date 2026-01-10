package com.nofrillsdb.jobs

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

@TestConfiguration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class TestSecurityConfig {

    @Bean
    fun testSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .authorizeHttpRequests { authz ->
                authz.anyRequest().permitAll()
            }
            .csrf { csrf -> csrf.disable() }
            .build()
    }
}
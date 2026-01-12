package com.nofrillsdb.config

import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

@Configuration
class DatabaseConfig {

    // --- MAIN APP DB (used by JPA + Flyway) ---

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    fun appDataSourceProperties() = DataSourceProperties()

    @Bean(name = ["dataSource"]) // important: standard name
    @Primary
    fun dataSource(@Qualifier("appDataSourceProperties") props: DataSourceProperties): DataSource =
        props.initializeDataSourceBuilder().build()


    // --- PROVISIONING DB (used only by your provisioning controller) ---

    @Bean
    @ConfigurationProperties("provisioning.datasource")
    fun provisioningDataSourceProperties() = DataSourceProperties()

    @Bean(name = ["provisionDataSource"])
    fun provisionDataSource(
        @Qualifier("provisioningDataSourceProperties") props: DataSourceProperties
    ): DataSource {
        log.warn("Provisioning DS props BEFORE build: url='{}' username='{}' driver='{}'",
            props.url, props.username, props.driverClassName
        )
        require(!props.url.isNullOrBlank()) { "Missing provisioning.datasource.url" }
        return props.initializeDataSourceBuilder()
            .type(HikariDataSource::class.java)
            .build()
    }

    @Bean
    fun provisionJdbcTemplate(@Qualifier("provisionDataSource") ds: DataSource) =
        JdbcTemplate(ds)

}
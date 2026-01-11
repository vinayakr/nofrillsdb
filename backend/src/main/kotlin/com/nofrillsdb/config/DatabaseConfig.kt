package com.nofrillsdb.config

import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Qualifier
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
    @ConfigurationProperties("provisioning.datasource.hikari")
    fun provisionDataSource(
        @Qualifier("provisioningDataSourceProperties") props: DataSourceProperties
    ): DataSource =
        props.initializeDataSourceBuilder()
            .build()

    @Bean
    fun provisionJdbcTemplate(@Qualifier("provisionDataSource") ds: DataSource) =
        JdbcTemplate(ds)
}
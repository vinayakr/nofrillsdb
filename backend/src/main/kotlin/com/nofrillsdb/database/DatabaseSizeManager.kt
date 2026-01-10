package com.nofrillsdb.database

import com.nofrillsdb.provisioning.Database
import com.nofrillsdb.utils.UserUtils
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

@Component
class DatabaseSizeManager (
    @Qualifier("provisionJdbcTemplate") private val jdbcTemplate: JdbcTemplate,
    private var userUtils: UserUtils) {
    fun getSizes(@AuthenticationPrincipal jwt: Jwt): Map<Database, Int> {
        val user = userUtils.getUser(jwt)
        if (user.databases.isEmpty()) return emptyMap()

        val dbByName = user.databases.associateBy { it.name }
        val dbNames = dbByName.keys.toTypedArray()

        val sql = """
        SELECT datname, pg_database_size(datname) AS size_bytes
        FROM pg_database
        WHERE datname = ANY (?)
    """.trimIndent()

        return jdbcTemplate.queryForList(sql, dbNames)
            .mapNotNull { row ->
                val name = row["datname"] as String
                val sizeBytes = (row["size_bytes"] as Number).toLong()

                val db = dbByName[name] ?: return@mapNotNull null

                // Convert Long → Int (bytes). Use toIntExact if you want to fail on overflow.
                db to sizeBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
            .toMap()
    }
}
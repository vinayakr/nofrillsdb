package com.nofrillsdb.database

import com.nofrillsdb.provisioning.Database
import com.nofrillsdb.utils.UserUtils
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/database")

class DatabaseController (
    private val databaseSizeManager: DatabaseSizeManager) {
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    fun getSizes(@AuthenticationPrincipal jwt: Jwt): Map<Database, Int> {
        return databaseSizeManager.getSizes(jwt)
    }
}

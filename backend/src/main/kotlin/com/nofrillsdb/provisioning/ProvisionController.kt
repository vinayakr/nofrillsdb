package com.nofrillsdb.provisioning

import com.nofrillsdb.exceptions.DatabaseAlreadyExistsException
import com.nofrillsdb.user.repository.UserRepository
import com.nofrillsdb.utils.JWTUtils
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/provision")
class ProvisionController(
    @Qualifier("provisionJdbcTemplate") private val jdbcTemplate: JdbcTemplate,
    private val userRepository: UserRepository
) {
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    fun createDatabase(@RequestBody req: CreateDBRequest, @AuthenticationPrincipal jwt: Jwt) : CreateDBResponse {
        val userId = JWTUtils.getUserId(jwt)
        val name = req.name + "_" + userId

        var user = userRepository.findById(userId).get();

        if (user.databases.contains(Database(name))) {
            throw DatabaseAlreadyExistsException("Database $name already exists")
        }

        jdbcTemplate.execute("CREATE ROLE $name LOGIN PASSWORD ${pgLiteral(req.password)}")
        jdbcTemplate.execute("CREATE DATABASE $name OWNER $name")
        jdbcTemplate.execute("REVOKE ALL ON DATABASE $name FROM PUBLIC")
        jdbcTemplate.execute("GRANT CONNECT, TEMPORARY ON DATABASE $name TO $name")

        user.databases += Database(name)
        userRepository.save(user)

        return CreateDBResponse(name, name)
    }

    private fun pgLiteral(s: String): String =
        "'" + s.replace("'", "''") + "'"
}
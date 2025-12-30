package com.nofrillsdb.provisioning

import com.nofrillsdb.provisioning.exception.DatabaseAlreadyExistsException
import com.nofrillsdb.user.exception.RoleAlreadyExistsException
import com.nofrillsdb.user.exception.RoleNotFoundException
import com.nofrillsdb.user.repository.UserRepository
import com.nofrillsdb.utils.JWTUtils
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RestController
@RequestMapping("/api/provision")
class ProvisionController(
    @Qualifier("provisionJdbcTemplate") private val jdbcTemplate: JdbcTemplate,
    private val credentialIssuer: MTLSCredentialIssuer,
    private val userRepository: UserRepository
) {

    @Value("\${provisioning.connection-limit}")
    private val connectionLimit: Int = 20

    @Value("\${provisioning.statement-timeout}")
    private val statementTimeout: String? = "10s"

    @Value("\${provisioning.pool-user}")
    private val poolUser: String = "pgbouncer_auth"

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    fun createDatabase(
        @RequestBody req: CreateDBRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): CreateDBResponse {

        val userId = JWTUtils.getUserId(jwt)
        val user = userRepository.findById(userId).orElseThrow()

        val dbName = req.name.trim()
        val loginRole = user.role ?: throw RoleNotFoundException("Role not found")
        val ownerRole = "owner_$loginRole"

        if (user.databases.any { it.name == dbName }) {
            throw DatabaseAlreadyExistsException("Database $dbName already exists")
        }

        /* -------------------------------------------------------------
         * 0) Ensure owner role exists
         * ------------------------------------------------------------- */
        jdbcTemplate.execute(
            """
            DO $$
            BEGIN
              IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = ${pgLiteral(ownerRole)}) THEN
                EXECUTE 'CREATE ROLE ${quoteIdent(ownerRole)} NOLOGIN';
              END IF;
            END
            $$;
            """.trimIndent()
        )

        /* -------------------------------------------------------------
         * 1) Allow provisioner to SET ROLE ownerRole
         * ------------------------------------------------------------- */
        jdbcTemplate.execute("""GRANT ${quoteIdent(ownerRole)} TO CURRENT_USER""")

        /* -------------------------------------------------------------
         * 2) Create database owned by ownerRole
         * ------------------------------------------------------------- */
        jdbcTemplate.execute(
            """CREATE DATABASE ${quoteIdent(dbName)} OWNER ${quoteIdent(ownerRole)}"""
        )

        /* -------------------------------------------------------------
         * 3) Database-level ACLs (must be executed as DB owner)
         * ------------------------------------------------------------- */
        jdbcTemplate.execute("""SET ROLE ${quoteIdent(ownerRole)}""")
        try {
            jdbcTemplate.execute(
                """REVOKE ALL ON DATABASE ${quoteIdent(dbName)} FROM PUBLIC"""
            )
            jdbcTemplate.execute(
                """GRANT CONNECT, TEMPORARY ON DATABASE ${quoteIdent(dbName)} TO ${quoteIdent(loginRole)}"""
            )
        } finally {
            jdbcTemplate.execute("""RESET ROLE""")
        }

        jdbcTemplate.execute("""SET ROLE ${quoteIdent(ownerRole)}""")
        try {
            jdbcTemplate.execute("""REVOKE ALL ON DATABASE ${quoteIdent(dbName)} FROM PUBLIC""")

            // allow tenant role (for direct connections, or future use)
            jdbcTemplate.execute("""GRANT CONNECT, TEMPORARY ON DATABASE ${quoteIdent(dbName)} TO ${quoteIdent(loginRole)}""")

            // allow pgbouncer pool user (required for *connections via pgbouncer*)
            jdbcTemplate.execute("""GRANT CONNECT, TEMPORARY ON DATABASE ${quoteIdent(dbName)} TO ${quoteIdent(poolUser)}""")
        } finally {
            jdbcTemplate.execute("""RESET ROLE""")
        }

        /* -------------------------------------------------------------
         * 4) Schema privileges inside the DB
         * ------------------------------------------------------------- */
        val dbJdbc = jdbcForDatabase(dbName)

        dbJdbc.execute("""REVOKE ALL ON SCHEMA public FROM PUBLIC""")
        dbJdbc.execute(
            """GRANT USAGE, CREATE ON SCHEMA public TO ${quoteIdent(loginRole)}"""
        )

        /* -------------------------------------------------------------
         * 5) Persist metadata
         * ------------------------------------------------------------- */
        user.databases += Database(dbName)
        userRepository.save(user)

        return CreateDBResponse(dbName)
    }

    /* =============================================================
     * Credential / role endpoints (unchanged behavior)
     * ============================================================= */

    @GetMapping("/crt")
    @PreAuthorize("isAuthenticated()")
    fun createCert(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<ByteArrayResource> {
        val user = userRepository.findById(JWTUtils.getUserId(jwt)).get()
        val role = user.role ?: credentialIssuer.generateRoleId()
        val issued = credentialIssuer.issueClientCredential(role)

        jdbcTemplate.execute(
            """
            DO $$ 
            BEGIN
                CREATE ROLE ${quoteIdent(issued.role)} LOGIN PASSWORD NULL;
            EXCEPTION WHEN duplicate_object THEN
                ALTER ROLE ${quoteIdent(issued.role)} LOGIN PASSWORD NULL;
            END 
            $$;
            """.trimIndent()
        )
        jdbcTemplate.execute("ALTER ROLE ${quoteIdent(issued.role)} NOINHERIT")
        jdbcTemplate.execute("ALTER ROLE ${quoteIdent(issued.role)} CONNECTION LIMIT $connectionLimit")
        jdbcTemplate.execute(
            "ALTER ROLE ${quoteIdent(issued.role)} SET statement_timeout = ${pgLiteral(statementTimeout ?: "10s")}"
        )

        user.expiresAt = issued.expiresAt
        user.serial = issued.serialHex
        user.fingerprint = issued.fingerprintSha256Hex
        user.issuedAt = issued.issuedAt
        userRepository.save(user)

        val zipBytes = createCredentialZip(
            issued.role,
            issued.privateKeyPem,
            issued.certificatePem
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_OCTET_STREAM
            setContentDispositionFormData("attachment", "${issued.role}_credentials.zip")
        }

        return ResponseEntity.ok()
            .headers(headers)
            .body(ByteArrayResource(zipBytes))
    }

    @GetMapping("/passwd")
    @PreAuthorize("isAuthenticated()")
    fun createPassword(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<ByteArrayResource> {
        val user = userRepository.findById(JWTUtils.getUserId(jwt)).get()
        val role = user.role ?: credentialIssuer.generateRoleId()
        val password = generateSecurePassword()

        if (user.role == null) {
            user.role = role
            userRepository.save(user)
        }

        jdbcTemplate.execute(
            """
            DO $$ 
            BEGIN
                CREATE ROLE ${quoteIdent(role)} LOGIN PASSWORD ${pgLiteral(password)};
            EXCEPTION WHEN duplicate_object THEN
                ALTER ROLE ${quoteIdent(role)} LOGIN PASSWORD ${pgLiteral(password)};
            END 
            $$;
            """.trimIndent()
        )
        jdbcTemplate.execute("ALTER ROLE ${quoteIdent(role)} NOINHERIT")
        jdbcTemplate.execute("ALTER ROLE ${quoteIdent(role)} CONNECTION LIMIT $connectionLimit")
        jdbcTemplate.execute(
            "ALTER ROLE ${quoteIdent(role)} SET statement_timeout = ${pgLiteral(statementTimeout ?: "10s")}"
        )

        val credentialsContent = "Role: $role\nPassword: $password\n"
        val credentialsBytes = credentialsContent.toByteArray()

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_OCTET_STREAM
            setContentDispositionFormData("attachment", "${role}_credentials.txt")
        }

        return ResponseEntity.ok()
            .headers(headers)
            .body(ByteArrayResource(credentialsBytes))
    }

    @PostMapping("/role")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    fun createRole(@AuthenticationPrincipal jwt: Jwt) {
        val user = userRepository.findById(JWTUtils.getUserId(jwt)).get()
        if (user.role != null) {
            throw RoleAlreadyExistsException("Role ${user.role} already exists")
        }
        val role = credentialIssuer.generateRoleId()
        user.role = role
        userRepository.save(user)
    }

    /* =============================================================
     * Helpers
     * ============================================================= */

    private fun jdbcForDatabase(dbName: String): JdbcTemplate {
        val provisioningHikari =
            jdbcTemplate.dataSource as com.zaxxer.hikari.HikariDataSource

        val newUrl = replaceJdbcDatabase(provisioningHikari.jdbcUrl, dbName)

        val cfg = com.zaxxer.hikari.HikariConfig().apply {
            jdbcUrl = newUrl
            driverClassName = provisioningHikari.driverClassName
            username = provisioningHikari.username
            password = provisioningHikari.password

            dataSourceProperties.putAll(provisioningHikari.dataSourceProperties)

            maximumPoolSize = 1
            minimumIdle = 0
            poolName = "prov-$dbName"
        }

        val ds = com.zaxxer.hikari.HikariDataSource(cfg)
        return JdbcTemplate(ds)
    }

    private fun replaceJdbcDatabase(jdbcUrl: String, dbName: String): String {
        require(jdbcUrl.startsWith("jdbc:postgresql:")) {
            "Not a postgres JDBC url: $jdbcUrl"
        }

        val uri = java.net.URI(jdbcUrl.removePrefix("jdbc:"))
        val host = uri.host ?: error("Missing host in $jdbcUrl")
        val port = if (uri.port == -1) 5432 else uri.port
        val query = uri.rawQuery

        return buildString {
            append("jdbc:postgresql://")
            append(host)
            append(":")
            append(port)
            append("/")
            append(dbName)
            if (!query.isNullOrBlank()) {
                append("?")
                append(query)
            }
        }
    }

    private fun generateSecurePassword(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*"
        val random = SecureRandom()
        return (1..16)
            .map { chars[random.nextInt(chars.length)] }
            .joinToString("")
    }

    private fun createCredentialZip(
        roleName: String,
        privateKeyPem: String,
        certificatePem: String
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("$roleName.key"))
            zip.write(privateKeyPem.toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("$roleName.crt"))
            zip.write(certificatePem.toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("clients_ca.crt"))
            val caCertContent = ClassPathResource("certs/clients_ca.crt")
                .inputStream.bufferedReader().use { it.readText() }
            zip.write(caCertContent.toByteArray())
            zip.closeEntry()
        }
        return baos.toByteArray()
    }

    private fun quoteIdent(ident: String): String = "\"$ident\""

    private fun pgLiteral(s: String): String =
        "'" + s.replace("'", "''") + "'"
}
package com.nofrillsdb.provisioning

import com.nofrillsdb.provisioning.exception.DatabaseAlreadyExistsException
import com.nofrillsdb.user.exception.CrtNotFoundException
import com.nofrillsdb.user.exception.DatabaseNotFoundException
import com.nofrillsdb.user.exception.RoleAlreadyExistsException
import com.nofrillsdb.user.exception.RoleNotFoundException
import com.nofrillsdb.user.repository.UserRepository
import com.nofrillsdb.utils.UserUtils
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
    private val userUtils: UserUtils,
    private val credentialIssuer: MTLSCredentialIssuer,
    private val userRepository: UserRepository
) {

    @Value("\${provisioning.connection-limit}")
    private val connectionLimit: Int = 20

    @Value("\${provisioning.statement-timeout}")
    private val statementTimeout: String = "10s"

    @Value("\${provisioning.pool-user}")
    private val poolUser: String = "pgbouncer_auth"

    @Value("\${provisioning.client-ca-crt-location}")
    private val clientCrtLocation: String? = "certs/clients_ca.crt"

    @GetMapping("/database")
    @PreAuthorize("isAuthenticated()")
    fun getDatabases(@AuthenticationPrincipal jwt: Jwt): Set<Database> {
        val user = userUtils.getUser(jwt)
        return user.databases
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    fun createDatabase(
        @RequestBody req: CreateDBRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): CreateDBResponse {

        val user = userUtils.getUser(jwt)

        // Password role is the stable base
        val pwdRole = user.role ?: throw RoleNotFoundException("Password role not found (user.role is null)")

        // Privileges and ownership are tied to pwdRole
        val privRole = "priv_$pwdRole"
        val ownerRole = "owner_$pwdRole"
        val roleUlid = pwdRole.removePrefix("role_")
        val dbName = "${req.name.trim()}_$roleUlid"

        if (user.databases.any { it.name == dbName }) {
            throw DatabaseAlreadyExistsException("Database $dbName already exists")
        }

        /* -------------------------------------------------------------
         * 0) Ensure priv + owner roles exist
         * ------------------------------------------------------------- */
        jdbcTemplate.execute(
            """
            DO $$
            BEGIN
              IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = ${pgLiteral(privRole)}) THEN
                EXECUTE 'CREATE ROLE ${quoteIdent(privRole)} NOLOGIN';
              END IF;

              IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = ${pgLiteral(ownerRole)}) THEN
                EXECUTE 'CREATE ROLE ${quoteIdent(ownerRole)} NOLOGIN';
              END IF;
            END
            $$;
            """.trimIndent()
        )

        // Allow provisioning user to SET ROLE ownerRole
        jdbcTemplate.execute("""GRANT ${quoteIdent(ownerRole)} TO CURRENT_USER""")

        jdbcTemplate.execute(
            """CREATE DATABASE ${quoteIdent(dbName)} OWNER ${quoteIdent(ownerRole)}"""
        )

        jdbcTemplate.execute("""SET ROLE ${quoteIdent(ownerRole)}""")
        try {
            jdbcTemplate.execute("""REVOKE ALL ON DATABASE ${quoteIdent(dbName)} FROM PUBLIC""")

            // Grant access to priv role (NOT directly to login roles)
            jdbcTemplate.execute(
                """GRANT CONNECT, TEMPORARY ON DATABASE ${quoteIdent(dbName)} TO ${quoteIdent(privRole)}"""
            )

            // allow pgbouncer pool user (required for *connections via pgbouncer*)
            jdbcTemplate.execute(
                """GRANT CONNECT, TEMPORARY ON DATABASE ${quoteIdent(dbName)} TO ${quoteIdent(poolUser)}"""
            )
        } finally {
            jdbcTemplate.execute("""RESET ROLE""")
        }

        val dbJdbc = jdbcForDatabase(dbName)

        try {
            dbJdbc.execute("""REVOKE ALL ON SCHEMA public FROM PUBLIC""")
            dbJdbc.execute(
                """GRANT USAGE, CREATE ON SCHEMA public TO ${quoteIdent(privRole)}"""
            )
        } finally {
            val dataSource = dbJdbc.dataSource as? com.zaxxer.hikari.HikariDataSource
            dataSource?.close()
        }

        user.databases += Database(dbName)
        userRepository.save(user)

        return CreateDBResponse(dbName)
    }

    @GetMapping("/crt")
    @PreAuthorize("isAuthenticated()")
    fun getCertificate(@AuthenticationPrincipal jwt: Jwt): CrtMetadataResponse {
        val user = userUtils.getUser(jwt)

        if (user.serial == null || user.fingerprint == null || user.issuedAt == null || user.expiresAt == null) {
            throw CrtNotFoundException("No CRT found for ${user.id}")
        }

        return CrtMetadataResponse(
            user.serial!!,
            user.fingerprint!!,
            user.issuedAt!!,
            user.expiresAt!!
        )
    }

    @PostMapping("/crt")
    @PreAuthorize("isAuthenticated()")
    fun createCert(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<ByteArrayResource> {
        val user = userUtils.getUser(jwt)

        // Stable base is password role
        val pwdRole = user.role ?: throw RoleNotFoundException("Password role not found (user.role is null)")
        val privRole = "priv_$pwdRole"

        // ROTATE: always create a new CRT role
        val oldCrtRole = user.crtRole
        val crtRole = "crt_${credentialIssuer.generateRoleId()}"

        user.crtRole = crtRole
        userRepository.save(user)

        val issued = credentialIssuer.issueClientCredential(crtRole)

        // Ensure CRT login role exists
        jdbcTemplate.execute(
            """
        DO $$ 
        BEGIN
            CREATE ROLE ${quoteIdent(crtRole)} LOGIN;
        EXCEPTION WHEN duplicate_object THEN
            ALTER ROLE ${quoteIdent(crtRole)} LOGIN;
        END 
        $$;
        """.trimIndent()
        )

        // Ensure priv role exists (NOLOGIN)
        jdbcTemplate.execute(
            """
        DO $$
        BEGIN
          IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = ${pgLiteral(privRole)}) THEN
            EXECUTE 'CREATE ROLE ${quoteIdent(privRole)} NOLOGIN';
          END IF;
        END
        $$;
        """.trimIndent()
        )

        // Membership so CRT role inherits privileges
        jdbcTemplate.execute("""GRANT ${quoteIdent(privRole)} TO ${quoteIdent(crtRole)}""")
        jdbcTemplate.execute("""ALTER ROLE ${quoteIdent(crtRole)} INHERIT""")

        // Apply settings
        jdbcTemplate.execute("""ALTER ROLE ${quoteIdent(crtRole)} CONNECTION LIMIT $connectionLimit""")
        jdbcTemplate.execute(
            """ALTER ROLE ${quoteIdent(crtRole)} SET statement_timeout = ${pgLiteral(statementTimeout ?: "10s")}"""
        )

        // OPTIONAL: immediately invalidate previous cert/role by disabling old role
        if (!oldCrtRole.isNullOrBlank() && oldCrtRole != crtRole) {
            jdbcTemplate.execute("""ALTER ROLE ${quoteIdent(oldCrtRole)} NOLOGIN""")
        }

        // Store metadata for "current" cert
        user.expiresAt = issued.expiresAt
        user.serial = issued.serialHex
        user.fingerprint = issued.fingerprintSha256Hex
        user.issuedAt = issued.issuedAt
        userRepository.save(user)

        val zipBytes = createCredentialZip(
            crtRole,
            issued.privateKeyPem,
            issued.certificatePem
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_OCTET_STREAM
            setContentDispositionFormData("attachment", "${crtRole}_credentials.zip")
        }

        return ResponseEntity.ok()
            .headers(headers)
            .body(ByteArrayResource(zipBytes))
    }

    @GetMapping("/passwd")
    @PreAuthorize("isAuthenticated()")
    fun createPassword(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<ByteArrayResource> {
        val user = userUtils.getUser(jwt)

        // Password role is stable
        val pwdRole = user.role ?: credentialIssuer.generateRoleId()
        val privRole = "priv_$pwdRole"

        val password = generateSecurePassword()

        if (user.role == null) {
            user.role = pwdRole
            userRepository.save(user)
        }

        // Ensure priv role exists
        jdbcTemplate.execute(
            """
            DO $$
            BEGIN
              IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = ${pgLiteral(privRole)}) THEN
                EXECUTE 'CREATE ROLE ${quoteIdent(privRole)} NOLOGIN';
              END IF;
            END
            $$;
            """.trimIndent()
        )

        // Ensure password login role exists + set password
        jdbcTemplate.execute(
            """
            DO $$ 
            BEGIN
                CREATE ROLE ${quoteIdent(pwdRole)} LOGIN PASSWORD ${pgLiteral(password)};
            EXCEPTION WHEN duplicate_object THEN
                ALTER ROLE ${quoteIdent(pwdRole)} LOGIN PASSWORD ${pgLiteral(password)};
            END 
            $$;
            """.trimIndent()
        )

        // Ensure membership so password role inherits privileges
        jdbcTemplate.execute("""GRANT ${quoteIdent(privRole)} TO ${quoteIdent(pwdRole)}""")
        jdbcTemplate.execute("ALTER ROLE ${quoteIdent(pwdRole)} INHERIT")

        // Your existing limits/settings
        jdbcTemplate.execute("ALTER ROLE ${quoteIdent(pwdRole)} CONNECTION LIMIT $connectionLimit")
        jdbcTemplate.execute(
            "ALTER ROLE ${quoteIdent(pwdRole)} SET statement_timeout = ${pgLiteral(statementTimeout ?: "10s")}"
        )

        val credentialsContent = "Role: $pwdRole\nPassword: $password\n"
        val credentialsBytes = credentialsContent.toByteArray()

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_OCTET_STREAM
            setContentDispositionFormData("attachment", "${pwdRole}_credentials.txt")
        }

        return ResponseEntity.ok()
            .headers(headers)
            .body(ByteArrayResource(credentialsBytes))
    }

    @PostMapping("/role")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    fun createRole(@AuthenticationPrincipal jwt: Jwt) {
        val user = userUtils.getUser(jwt)
        if (user.role != null) {
            throw RoleAlreadyExistsException("Role ${user.role} already exists")
        }

        // Stable base password role
        val pwdRole = credentialIssuer.generateRoleId()
        val privRole = "priv_$pwdRole"
        val ownerRole = "owner_$pwdRole"

        user.role = pwdRole
        userRepository.save(user)

        // Create priv + owner (NOLOGIN) and pwdRole (LOGIN)
        jdbcTemplate.execute(
            """
            DO $$
            BEGIN
              IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = ${pgLiteral(privRole)}) THEN
                EXECUTE 'CREATE ROLE ${quoteIdent(privRole)} NOLOGIN';
              END IF;

              IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = ${pgLiteral(ownerRole)}) THEN
                EXECUTE 'CREATE ROLE ${quoteIdent(ownerRole)} NOLOGIN';
              END IF;

              IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = ${pgLiteral(pwdRole)}) THEN
                EXECUTE 'CREATE ROLE ${quoteIdent(pwdRole)} LOGIN';
              ELSE
                EXECUTE 'ALTER ROLE ${quoteIdent(pwdRole)} LOGIN';
              END IF;

              -- Membership: pwdRole inherits privileges
              EXECUTE 'GRANT ${quoteIdent(privRole)} TO ${quoteIdent(pwdRole)}';
              EXECUTE 'ALTER ROLE ${quoteIdent(pwdRole)} INHERIT';
            END
            $$;
            """.trimIndent()
        )
    }

    @DeleteMapping("/db/{name}")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteDatabase(@AuthenticationPrincipal jwt: Jwt, @PathVariable("name") name: String) {
        val user = userUtils.getUser(jwt)
        val db = user.databases.find{it.name == name} ?: throw DatabaseNotFoundException("Database $name not found")

        jdbcTemplate.execute(
            """REVOKE CONNECT ON DATABASE ${quoteIdent(name)} FROM PUBLIC"""
        )
        jdbcTemplate.execute(
            """
        SELECT pg_terminate_backend(pid)
        FROM pg_stat_activity
        WHERE datname = ${pgLiteral(name)}
          AND pid <> pg_backend_pid();
        """.trimIndent()
        )

        jdbcTemplate.execute(
            """DROP DATABASE ${quoteIdent(name)}"""
        )

        user.databases.remove(db)
        userRepository.save(user)
    }
    private fun jdbcForDatabase(dbName: String): JdbcTemplate {
        val provisioningHikari = jdbcTemplate.dataSource as com.zaxxer.hikari.HikariDataSource
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
        require(jdbcUrl.startsWith("jdbc:postgresql:")) { "Not a postgres JDBC url: $jdbcUrl" }

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
        return (1..16).map { chars[random.nextInt(chars.length)] }.joinToString("")
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

    private fun pgLiteral(s: String): String = "'" + s.replace("'", "''") + "'"
}
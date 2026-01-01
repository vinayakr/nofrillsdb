package com.nofrillsdb.users.model.db

import com.nofrillsdb.provisioning.Database
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(unique = true, nullable = false)
    @Email(message = "Email must be valid")
    @NotBlank(message = "Email is required")
    val email: String = "",
    val name: String? = null,
    var role: String? = null,
    var crtRole: String? = null,
    var serial: String? = null,
    var fingerprint: String? = null,
    var issuedAt: Instant? = null,
    var expiresAt: Instant? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    var databases: Set<Database> = mutableSetOf()
)
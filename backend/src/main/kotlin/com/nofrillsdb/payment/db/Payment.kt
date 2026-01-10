package com.nofrillsdb.payment.db

import com.nofrillsdb.users.model.db.User
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "payments")
data class Payment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "stripe_customer_id", unique = true, nullable = false)
    val stripeCustomerId: String,

    @Column(name = "default_payment_method_id")
    var defaultPaymentMethodId: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
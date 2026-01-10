package com.nofrillsdb.payment.repository

import com.nofrillsdb.payment.db.Payment
import com.nofrillsdb.users.model.db.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface PaymentRepository : JpaRepository<Payment, Long> {
    fun findByUser(user: User): Optional<Payment>
    fun findByStripeCustomerId(stripeCustomerId: String): Optional<Payment>
}
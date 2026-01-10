package com.nofrillsdb.payment

import com.nofrillsdb.payment.db.Payment
import com.nofrillsdb.payment.repository.PaymentRepository
import com.nofrillsdb.users.model.db.User
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class PaymentManager(
    private val paymentRepository: PaymentRepository,
    private val stripeService: StripeService
) {

    fun getOrCreatePaymentForUser(user: User): Payment {
        return paymentRepository.findByUser(user).orElseGet {
            val stripeCustomer = stripeService.createCustomer(user.email, user.name)
            val payment = Payment(
                user = user,
                stripeCustomerId = stripeCustomer.id,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
            paymentRepository.save(payment)
        }
    }

    fun updateDefaultPaymentMethod(user: User, paymentMethodId: String): Payment {
        val payment = getOrCreatePaymentForUser(user)
        val updatedPayment = payment.copy(
            defaultPaymentMethodId = paymentMethodId,
            updatedAt = Instant.now()
        )
        return paymentRepository.save(updatedPayment)
    }

    fun clearDefaultPaymentMethod(user: User): Payment? {
        val payment = getPaymentByUser(user) ?: return null
        val updatedPayment = payment.copy(
            defaultPaymentMethodId = null,
            updatedAt = Instant.now()
        )
        return paymentRepository.save(updatedPayment)
    }

    fun getPaymentByUser(user: User): Payment? {
        return paymentRepository.findByUser(user).orElse(null)
    }
}
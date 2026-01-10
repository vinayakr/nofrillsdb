package com.nofrillsdb.payment

import com.nofrillsdb.user.UserManager
import com.nofrillsdb.utils.JWTUtils
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank

@RestController
@RequestMapping("/api/payments")
class PaymentController(
    private val stripeService: StripeService,
    private val userManager: UserManager,
    private val paymentManager: PaymentManager
) {

    @PostMapping("/setupIntent")
    fun createSetupIntent(@AuthenticationPrincipal jwt: Jwt): SetupIntentResponse {
        val userId = JWTUtils.getUserId(jwt)
        val user = userManager.getUserById(userId)
        val payment = paymentManager.getOrCreatePaymentForUser(user)

        val setupIntentData = stripeService.createSetupIntent(payment.stripeCustomerId)

        return SetupIntentResponse(
            clientSecret = setupIntentData["client_secret"] as String,
            customerId = payment.stripeCustomerId
        )
    }

    @PostMapping("/attachPaymentMethod")
    fun attachPaymentMethod(
        @Valid @RequestBody request: AttachPaymentMethodRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): PaymentMethodResponse {
        val userId = JWTUtils.getUserId(jwt)
        val user = userManager.getUserById(userId)
        val payment = paymentManager.getOrCreatePaymentForUser(user)

        // If there's already a payment method, detach it first
        payment.defaultPaymentMethodId?.let { paymentMethodId ->
            stripeService.detachPaymentMethod(paymentMethodId)
        }

        val paymentMethod = stripeService.attachPaymentMethod(
            request.paymentMethodId,
            payment.stripeCustomerId
        )

        // Always update to the new payment method (since we only allow one)
        paymentManager.updateDefaultPaymentMethod(user, paymentMethod.id)

        return PaymentMethodResponse(
            id = paymentMethod.id,
            type = paymentMethod.type,
            card = paymentMethod.card?.let { card ->
                CardDetails(
                    brand = card.brand ?: "unknown",
                    last4 = card.last4 ?: "****",
                    expMonth = card.expMonth ?: 0,
                    expYear = card.expYear ?: 0
                )
            }
        )
    }

    @GetMapping("/paymentMethods")
    fun getPaymentMethods(@AuthenticationPrincipal jwt: Jwt): PaymentMethodsResponse {
        val userId = JWTUtils.getUserId(jwt)
        val user = userManager.getUserById(userId)
        val payment = paymentManager.getPaymentByUser(user)

        if (payment?.defaultPaymentMethodId == null) {
            return PaymentMethodsResponse(emptyList())
        }

        // Only return the current default payment method
        return try {
            val paymentMethod = stripeService.getPaymentMethod(payment.defaultPaymentMethodId!!)
            PaymentMethodsResponse(
                paymentMethods = listOf(
                    PaymentMethodResponse(
                        id = paymentMethod.id,
                        type = paymentMethod.type,
                        card = paymentMethod.card?.let { card ->
                            CardDetails(
                                brand = card.brand ?: "unknown",
                                last4 = card.last4 ?: "****",
                                expMonth = card.expMonth?.toLong() ?: 0,
                                expYear = card.expYear?.toLong() ?: 0
                            )
                        }
                    )
                )
            )
        } catch (_: Exception) {
            // If the payment method doesn't exist anymore, clear it from our records
            paymentManager.clearDefaultPaymentMethod(user)
            PaymentMethodsResponse(emptyList())
        }
    }
}

data class SetupIntentResponse(
    val clientSecret: String,
    val customerId: String
)

data class AttachPaymentMethodRequest(
    @field:NotBlank(message = "Payment method ID is required")
    val paymentMethodId: String
)

data class PaymentMethodResponse(
    val id: String,
    val type: String,
    val card: CardDetails?
)

data class CardDetails(
    val brand: String,
    val last4: String,
    val expMonth: Long,
    val expYear: Long
)

data class PaymentMethodsResponse(
    val paymentMethods: List<PaymentMethodResponse>
)
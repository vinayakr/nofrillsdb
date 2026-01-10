package com.nofrillsdb.payment

import com.stripe.Stripe
import com.stripe.model.Customer
import com.stripe.model.PaymentIntent
import com.stripe.model.PaymentMethod
import com.stripe.model.Invoice
import com.stripe.model.InvoiceItem
import com.stripe.param.CustomerCreateParams
import com.stripe.param.PaymentIntentCreateParams
import com.stripe.param.PaymentMethodAttachParams
import com.stripe.param.InvoiceCreateParams
import com.stripe.param.InvoiceItemCreateParams
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import jakarta.annotation.PostConstruct

@Service
class StripeService(
    @Value("\${stripe.secret-key:}") private val stripeSecretKey: String
) {

    @PostConstruct
    fun init() {
        if (stripeSecretKey.isNotBlank()) {
            Stripe.apiKey = stripeSecretKey
        }
    }

    fun createCustomer(email: String, name: String?): Customer {
        val params = CustomerCreateParams.builder()
            .setEmail(email)
            .apply {
                if (name != null) {
                    setName(name)
                }
            }
            .build()

        return Customer.create(params)
    }

    fun createSetupIntent(customerId: String): Map<String, Any> {
        val params = com.stripe.param.SetupIntentCreateParams.builder()
            .setCustomer(customerId)
            .setUsage(com.stripe.param.SetupIntentCreateParams.Usage.OFF_SESSION)
            .build()

        val setupIntent = com.stripe.model.SetupIntent.create(params)

        return mapOf(
            "client_secret" to setupIntent.clientSecret,
            "setup_intent_id" to setupIntent.id
        )
    }

    fun attachPaymentMethod(paymentMethodId: String, customerId: String): PaymentMethod {
        val paymentMethod = PaymentMethod.retrieve(paymentMethodId)
        val params = PaymentMethodAttachParams.builder()
            .setCustomer(customerId)
            .build()

        return paymentMethod.attach(params)
    }

    fun detachPaymentMethod(paymentMethodId: String): PaymentMethod {
        val paymentMethod = PaymentMethod.retrieve(paymentMethodId)
        return paymentMethod.detach()
    }

    fun getPaymentMethod(paymentMethodId: String): PaymentMethod {
        return PaymentMethod.retrieve(paymentMethodId)
    }

    fun getCustomerPaymentMethods(customerId: String): List<Map<String, Any>> {
        val params = com.stripe.param.PaymentMethodListParams.builder()
            .setCustomer(customerId)
            .setType(com.stripe.param.PaymentMethodListParams.Type.CARD)
            .build()

        val paymentMethods = PaymentMethod.list(params)

        return paymentMethods.data.map { pm ->
            mapOf(
                "id" to pm.id,
                "type" to pm.type,
                "card" to mapOf(
                    "brand" to (pm.card?.brand ?: "unknown"),
                    "last4" to (pm.card?.last4 ?: "****"),
                    "exp_month" to (pm.card?.expMonth ?: 0),
                    "exp_year" to (pm.card?.expYear ?: 0)
                )
            )
        }
    }

    fun createPaymentIntent(
        amount: Long, // Amount in cents
        currency: String = "usd",
        customerId: String,
        paymentMethodId: String? = null
    ): PaymentIntent {
        val params = PaymentIntentCreateParams.builder()
            .setAmount(amount)
            .setCurrency(currency)
            .setCustomer(customerId)
            .apply {
                if (paymentMethodId != null) {
                    setPaymentMethod(paymentMethodId)
                    setConfirmationMethod(PaymentIntentCreateParams.ConfirmationMethod.MANUAL)
                    setConfirm(true)
                }
            }
            .build()

        return PaymentIntent.create(params)
    }

    fun createDraftInvoice(
        customerId: String,
        description: String,
        amount: Long, // Amount in cents
        currency: String = "usd",
        periodStart: Long, // Unix timestamp
        periodEnd: Long    // Unix timestamp
    ): Invoice {
        // First create an invoice item
        val invoiceItemParams = InvoiceItemCreateParams.builder()
            .setCustomer(customerId)
            .setDescription(description)
            .setAmount(amount)
            .setCurrency(currency)
            .setPeriod(
                InvoiceItemCreateParams.Period.builder()
                    .setStart(periodStart)
                    .setEnd(periodEnd)
                    .build()
            )
            .build()

        InvoiceItem.create(invoiceItemParams)

        // Then create a draft invoice
        val invoiceParams = InvoiceCreateParams.builder()
            .setCustomer(customerId)
            .setAutoAdvance(false) // Keep as draft, don't auto-finalize
            .setCollectionMethod(InvoiceCreateParams.CollectionMethod.CHARGE_AUTOMATICALLY)
            .build()

        return Invoice.create(invoiceParams)
    }
}
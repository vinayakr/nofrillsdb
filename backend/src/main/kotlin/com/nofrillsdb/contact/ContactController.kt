package com.nofrillsdb.contact

import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class ContactController(
    private val gmailService: GmailService
) {

    private val logger = LoggerFactory.getLogger(ContactController::class.java)

    @PostMapping("/contact")
    fun sendContactEmail(@Valid @RequestBody request: ContactRequest): ResponseEntity<ContactResponse> {
        return try {
            logger.info("Received contact form submission from: ${request.email}")

            gmailService.sendContactEmail(
                request.email,
                request.name,
                request.message
            )

            logger.info("Successfully sent contact email from: ${request.email}")

            ResponseEntity.ok(
                ContactResponse(
                    success = true,
                    message = "Thank you for your message! We'll get back to you soon."
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to send contact email from: ${request.email}", e)

            ResponseEntity.status(500).body(
                ContactResponse(
                    success = false,
                    message = "There was an error sending your message. Please try again later."
                )
            )
        }
    }
}

data class ContactRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Please provide a valid email address")
    val email: String,

    val name: String,

    @field:NotBlank(message = "Message is required")
    val message: String
)

data class ContactResponse(
    val success: Boolean,
    val message: String
)
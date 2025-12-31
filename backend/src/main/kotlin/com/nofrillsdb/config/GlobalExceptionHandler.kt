package com.nofrillsdb.config

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationExceptions(ex: MethodArgumentNotValidException): ResponseEntity<Map<String, String>> {
        val errors = mutableMapOf<String, String>()

        ex.bindingResult.fieldErrors.forEach { error ->
            val fieldName = error.field
            val errorMessage = when (error.code) {
                "NotBlank" -> when (fieldName) {
                    "name" -> "Database name cannot be empty"
                    else -> "This field cannot be empty"
                }
                "Size" -> when (fieldName) {
                    "name" -> "Database name must be between 3 and 40 characters"
                    else -> "Field size is invalid"
                }
                "Pattern" -> when (fieldName) {
                    "name" -> "Database name must start with a letter or underscore and contain only letters, numbers, and underscores"
                    else -> "Field format is invalid"
                }
                else -> error.defaultMessage ?: "Invalid value"
            }
            errors[fieldName] = errorMessage
        }

        // Return the first error message as the main message
        val mainMessage = errors.values.firstOrNull() ?: "Validation failed"
        return ResponseEntity.badRequest().body(mapOf("message" to mainMessage))
    }
}
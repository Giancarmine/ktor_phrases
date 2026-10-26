package com.ges.model

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val error: String, val message: String)

class ValidationException(override val message: String) : RuntimeException(message)

class PhraseNotFoundException(id: Int) : RuntimeException("No phrase found for id $id")

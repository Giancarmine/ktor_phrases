package com.ges.model

import kotlinx.serialization.Serializable

/**
 * Incoming payload for `POST /api/v1/phrases`.
 *
 * Fields default to empty strings so that missing values are reported as validation errors
 * (400) instead of JSON parsing errors.
 */
@Serializable
data class PhraseRequest(
    val emoji: String = "",
    val phrase: String = "",
)

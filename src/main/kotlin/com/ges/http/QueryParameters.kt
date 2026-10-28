package com.ges.http

import com.ges.model.ValidationException
import io.ktor.http.Parameters

/**
 * Reads a bounded integer query parameter or throws a [ValidationException] with a stable
 * message. Shared by the JSON API and the server-rendered view.
 */
internal fun Parameters.queryInt(name: String, default: Int, min: Int, max: Int? = null): Int {
    val raw = this[name] ?: return default
    val value = raw.toIntOrNull() ?: throw ValidationException("$name must be an integer")
    if (value < min) throw ValidationException("$name must be greater than or equal to $min")
    if (max != null && value > max) {
        throw ValidationException("$name must be less than or equal to $max")
    }
    return value
}

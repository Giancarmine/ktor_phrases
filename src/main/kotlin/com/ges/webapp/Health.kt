package com.ges.webapp

import com.ges.model.HealthResponse
import com.ges.repository.Repository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.CancellationException

const val HEALTH = "/health"
const val READY = "/ready"

fun Route.health(repository: Repository) {
    get(HEALTH) {
        call.respond(HealthResponse(status = "ok"))
    }

    get(READY) {
        try {
            // Touch the configured backend instead of reporting process liveness only.
            repository.count()
            call.respond(HealthResponse(status = "ready"))
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            call.application.log.error("Readiness check failed", cause)
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                HealthResponse(status = "unavailable"),
            )
        }
    }
}

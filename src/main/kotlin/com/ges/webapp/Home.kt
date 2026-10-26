package com.ges.webapp

import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

const val HOME = "/"

fun Route.home() {
    get(HOME) {
        call.respondText(
            """
            ktor_phrases - emoji/phrase API

            GET    /api/v1/phrases?limit=20&offset=0
            POST   /api/v1/phrases
            GET    /api/v1/phrases/{id}
            DELETE /api/v1/phrases/{id}
            GET    /phrases
            GET    /docs
            GET    /about
            GET    /health
            GET    /ready
            """.trimIndent(),
            ContentType.Text.Plain,
        )
    }
}

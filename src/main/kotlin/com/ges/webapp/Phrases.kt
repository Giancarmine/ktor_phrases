package com.ges.webapp

import com.ges.repository.Repository
import io.ktor.server.application.call
import io.ktor.server.freemarker.FreeMarkerContent
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

const val PHRASES = "/phrases"

fun Route.phrases(repository: Repository) {
    get(PHRASES) {
        call.respond(
            FreeMarkerContent("phrases.ftl", mapOf("phrases" to repository.phrases())),
        )
    }
}

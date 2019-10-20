package com.ges.webapp

import com.ges.repository.*
import io.ktor.application.call
import io.ktor.freemarker.*
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get

const val PHRASES = "/phrases"

fun Route.phrases(db: Repository) {
    get(PHRASES) {
        val phrases = db.phrases()
        call.respond(FreeMarkerContent("phrases.ftl", mapOf("phrases" to phrases)))
    }
}
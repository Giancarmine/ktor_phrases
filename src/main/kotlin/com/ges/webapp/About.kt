package com.ges.webapp

import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

const val ABOUT = "/about"

fun Route.about() {
    get(ABOUT) {
        call.respondText(
            "ktor_phrases - a small Ktor demo API by @El_Giancar. " +
                "Source: https://github.com/Giancarmine/ktor_phrases",
        )
    }
}

package com.ges.webapp

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*

const val ABOUT = "/about"

fun Route.about() {
    get(ABOUT) {
        call.respondText("About page.")
    }
}
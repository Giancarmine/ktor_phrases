package com.ges.webapp

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*

const val HOME = "/"

fun Route.home() {
    get (HOME) {
        call.respondText("Hello, Ktor!")
    }
}
package com.ges

import com.ges.api.phraseRoutes
import com.ges.model.ErrorResponse
import com.ges.model.PhraseNotFoundException
import com.ges.model.ValidationException
import com.ges.repository.InMemoryRepository
import com.ges.repository.Repository
import com.ges.webapp.about
import com.ges.webapp.home
import com.ges.webapp.phrases
import freemarker.cache.ClassTemplateLoader
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.freemarker.FreeMarker
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

fun main(args: Array<String>): Unit = EngineMain.main(args)

private val apiJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

@Suppress("unused") // Referenced from application.conf.
fun Application.module(repository: Repository = InMemoryRepository()) {
    install(DefaultHeaders)

    install(StatusPages) {
        exception<ValidationException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("validation_error", cause.message),
            )
        }
        exception<PhraseNotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("not_found", cause.message ?: "Phrase not found"),
            )
        }
        exception<ContentTransformationException> { call, _ ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("invalid_body", "Request body is not valid JSON for this endpoint"),
            )
        }
        exception<BadRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("bad_request", cause.message ?: "Malformed request"),
            )
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception while serving ${call.request.uri}", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("internal_error", "Unexpected server error"),
            )
        }
    }

    install(ContentNegotiation) {
        json(apiJson)
    }

    install(FreeMarker) {
        templateLoader = ClassTemplateLoader(Application::class.java.classLoader, "templates")
    }

    routing {
        home()
        about()
        phrases(repository)
        phraseRoutes(repository)
    }
}

const val API_VERSION = "/api/v1"

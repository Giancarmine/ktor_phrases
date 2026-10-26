package com.ges.api

import com.ges.API_VERSION
import com.ges.model.PhraseNotFoundException
import com.ges.model.PhraseRequest
import com.ges.model.ValidationException
import com.ges.repository.Repository
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

const val PHRASES_ENDPOINT = "$API_VERSION/phrases"

fun Route.phraseRoutes(repository: Repository) {
    route(PHRASES_ENDPOINT) {
        get {
            call.respond(repository.phrases())
        }

        post {
            val request = call.receive<PhraseRequest>().validated()
            val result = repository.add(request.emoji, request.phrase)

            if (result.created) {
                call.response.header(HttpHeaders.Location, "$PHRASES_ENDPOINT/${result.phrase.id}")
            }
            call.respond(
                if (result.created) HttpStatusCode.Created else HttpStatusCode.OK,
                result.phrase,
            )
        }

        get("/{id}") {
            val id = call.parameters.requireId()
            call.respond(repository.phrase(id) ?: throw PhraseNotFoundException(id))
        }

        delete("/{id}") {
            val id = call.parameters.requireId()
            if (!repository.remove(id)) throw PhraseNotFoundException(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun PhraseRequest.validated(): PhraseRequest {
    val normalized = copy(emoji = emoji.trim(), phrase = phrase.trim())
    val errors = buildList {
        if (normalized.emoji.isBlank()) {
            add("emoji must not be blank")
        } else if (normalized.emoji.length > MAX_EMOJI_LENGTH) {
            add("emoji must be at most $MAX_EMOJI_LENGTH characters")
        }
        if (normalized.phrase.isBlank()) {
            add("phrase must not be blank")
        } else if (normalized.phrase.length > MAX_PHRASE_LENGTH) {
            add("phrase must be at most $MAX_PHRASE_LENGTH characters")
        }
    }
    if (errors.isNotEmpty()) throw ValidationException(errors.joinToString("; "))
    return normalized
}

private fun Parameters.requireId(): Int =
    this["id"]?.toIntOrNull()?.takeIf { it > 0 }
        ?: throw ValidationException("id must be a positive integer")

private const val MAX_EMOJI_LENGTH = 32
private const val MAX_PHRASE_LENGTH = 280

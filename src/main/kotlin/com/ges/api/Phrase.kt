package com.ges.api

import com.ges.API_VERSION
import com.ges.config.AppConfig
import com.ges.http.queryInt
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

fun Route.phraseRoutes(repository: Repository, config: AppConfig) {
    route(PHRASES_ENDPOINT) {
        get {
            val offset = call.parameters.queryInt("offset", default = 0, min = 0)
            val limit = call.parameters.queryInt(
                "limit",
                default = config.pageSize,
                min = 1,
                max = config.maxPageSize,
            )
            val page = repository.page(offset, limit)
            val total = page.total

            call.response.header("X-Total-Count", total.toString())
            call.response.header("X-Offset", offset.toString())
            call.response.header("X-Limit", limit.toString())
            paginationLinks(offset, limit, total, page.items.size)?.let { links ->
                call.response.header(HttpHeaders.Link, links)
            }

            call.respond(page.items)
        }

        post {
            val request = call.receive<PhraseRequest>().validated(config)
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

private fun PhraseRequest.validated(config: AppConfig): PhraseRequest {
    val normalized = copy(emoji = emoji.trim(), phrase = phrase.trim())
    val errors = buildList {
        if (normalized.emoji.isBlank()) {
            add("emoji must not be blank")
        } else if (normalized.emoji.length > config.maxEmojiLength) {
            add("emoji must be at most ${config.maxEmojiLength} characters")
        }
        if (normalized.phrase.isBlank()) {
            add("phrase must not be blank")
        } else if (normalized.phrase.length > config.maxPhraseLength) {
            add("phrase must be at most ${config.maxPhraseLength} characters")
        }
    }
    if (errors.isNotEmpty()) throw ValidationException(errors.joinToString("; "))
    return normalized
}

private fun Parameters.requireId(): Int =
    this["id"]?.toIntOrNull()?.takeIf { it > 0 }
        ?: throw ValidationException("id must be a positive integer")

private fun paginationLinks(offset: Int, limit: Int, total: Int, returned: Int): String? {
    val links = buildList {
        val next = offset + returned
        if (next < total) {
            add("<$PHRASES_ENDPOINT?limit=$limit&offset=$next>; rel=\"next\"")
        }
        if (offset > 0) {
            add("<$PHRASES_ENDPOINT?limit=$limit&offset=${maxOf(0, offset - limit)}>; rel=\"prev\"")
        }
    }
    return links.takeIf { it.isNotEmpty() }?.joinToString(", ")
}

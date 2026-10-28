package com.ges.webapp

import com.ges.config.AppConfig
import com.ges.http.queryInt
import com.ges.repository.Repository
import io.ktor.server.application.call
import io.ktor.server.freemarker.FreeMarkerContent
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

const val PHRASES = "/phrases"

fun Route.phrases(repository: Repository, config: AppConfig) {
    get(PHRASES) {
        val offset = call.parameters.queryInt("offset", default = 0, min = 0)
        val limit = call.parameters.queryInt(
            "limit",
            default = config.pageSize,
            min = 1,
            max = config.maxPageSize,
        )
        val page = repository.page(offset, limit)
        val nextOffset = offset + page.items.size

        call.respond(
            FreeMarkerContent(
                "phrases.ftl",
                mapOf(
                    "phrases" to page.items,
                    "total" to page.total,
                    "offset" to offset,
                    "limit" to limit,
                    "prevOffset" to maxOf(0, offset - limit),
                    "nextOffset" to nextOffset,
                    "hasPrev" to (offset > 0),
                    "hasNext" to (nextOffset < page.total),
                ),
            ),
        )
    }
}

package com.ges

import com.ges.config.AppConfig
import com.ges.repository.InMemoryRepository
import com.ges.repository.Repository
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json

internal fun appTest(
    repository: Repository = InMemoryRepository(),
    config: AppConfig = AppConfig(),
    block: suspend ApplicationTestBuilder.() -> Unit,
) = testApplication {
    application { configure(repository, config) }
    block()
}

internal fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}

internal fun HttpRequestBuilder.jsonBody(body: String) {
    contentType(ContentType.Application.Json)
    setBody(body)
}

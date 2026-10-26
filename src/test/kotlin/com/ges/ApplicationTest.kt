package com.ges

import com.ges.model.EmojiPhrase
import com.ges.model.ErrorResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { module() }
        block()
    }

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    @Test
    fun `home describes the service`() = withApp {
        val response = client.get("/")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("/api/v1/phrases"))
    }

    @Test
    fun `create returns 201 with a location header`() = withApp {
        val response = jsonClient().post("/api/v1/phrases") {
            contentType(ContentType.Application.Json)
            setBody("""{"emoji":"🚿","phrase":"Take a shower!"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("/api/v1/phrases/1", response.headers[HttpHeaders.Location])

        val created: EmojiPhrase = response.body()
        assertEquals(1, created.id)
        assertEquals("🚿", created.emoji)
        assertEquals("Take a shower!", created.phrase)
    }

    @Test
    fun `duplicate posts return the existing phrase`() = withApp {
        val client = jsonClient()
        val payload = """{"emoji":"🚿","phrase":"Take a shower!"}"""

        val first: EmojiPhrase = client.post("/api/v1/phrases") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }.body()
        val secondResponse = client.post("/api/v1/phrases") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val second: EmojiPhrase = secondResponse.body()

        assertEquals(HttpStatusCode.OK, secondResponse.status)
        assertEquals(first, second)
    }

    @Test
    fun `list and fetch phrases`() = withApp {
        val client = jsonClient()
        client.post("/api/v1/phrases") {
            contentType(ContentType.Application.Json)
            setBody("""{"emoji":"☕","phrase":"Coffee break"}""")
        }

        val all: List<EmojiPhrase> = client.get("/api/v1/phrases").body()
        assertEquals(1, all.size)

        val one: EmojiPhrase = client.get("/api/v1/phrases/${all.single().id}").body()
        assertEquals(all.single(), one)
    }

    @Test
    fun `delete removes the phrase`() = withApp {
        val client = jsonClient()
        val created: EmojiPhrase = client.post("/api/v1/phrases") {
            contentType(ContentType.Application.Json)
            setBody("""{"emoji":"🌙","phrase":"Good night"}""")
        }.body()

        assertEquals(HttpStatusCode.NoContent, client.delete("/api/v1/phrases/${created.id}").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/phrases/${created.id}").status)
    }

    @Test
    fun `blank fields are rejected with 400`() = withApp {
        val response = jsonClient().post("/api/v1/phrases") {
            contentType(ContentType.Application.Json)
            setBody("""{"emoji":" ","phrase":""}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error: ErrorResponse = response.body()
        assertEquals("validation_error", error.error)
    }

    @Test
    fun `unknown id returns 404`() = withApp {
        val response = jsonClient().get("/api/v1/phrases/999")

        assertEquals(HttpStatusCode.NotFound, response.status)
        val error: ErrorResponse = response.body()
        assertEquals("not_found", error.error)
    }

    @Test
    fun `malformed json returns 400`() = withApp {
        val response = jsonClient().post("/api/v1/phrases") {
            contentType(ContentType.Application.Json)
            setBody("""{"emoji":""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}

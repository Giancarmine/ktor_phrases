package com.ges

import com.ges.model.EmojiPhrase
import com.ges.model.ErrorResponse
import com.ges.repository.InMemoryRepository
import com.ges.repository.Repository
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiTest {

    @Test
    fun `empty collection returns an empty json array`() = appTest {
        val response = jsonClient().get("/api/v1/phrases")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())

        val phrases: List<EmojiPhrase> = response.body()
        assertTrue(phrases.isEmpty())
    }

    @Test
    fun `create returns 201 with a location header and the stored phrase`() = appTest {
        val response = jsonClient().post("/api/v1/phrases") {
            jsonBody("""{"emoji":"🚿","phrase":"Take a shower!"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("/api/v1/phrases/1", response.headers[HttpHeaders.Location])
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())

        val created: EmojiPhrase = response.body()
        assertEquals(EmojiPhrase(id = 1, emoji = "🚿", phrase = "Take a shower!"), created)
    }

    @Test
    fun `create trims surrounding whitespace`() = appTest {
        val created: EmojiPhrase = jsonClient().post("/api/v1/phrases") {
            jsonBody("""{"emoji":"  🚿  ","phrase":"  Take a shower!  "}""")
        }.body()

        assertEquals("🚿", created.emoji)
        assertEquals("Take a shower!", created.phrase)
    }

    @Test
    fun `duplicate pairs return 200 with the existing phrase`() = appTest {
        val client = jsonClient()
        val payload = """{"emoji":"🚿","phrase":"Take a shower!"}"""

        val first: EmojiPhrase = client.post("/api/v1/phrases") { jsonBody(payload) }.body()
        val duplicateResponse = client.post("/api/v1/phrases") { jsonBody(payload) }
        val duplicate: EmojiPhrase = duplicateResponse.body()

        assertEquals(HttpStatusCode.OK, duplicateResponse.status)
        assertNull(duplicateResponse.headers[HttpHeaders.Location])
        assertEquals(first, duplicate)

        val all: List<EmojiPhrase> = client.get("/api/v1/phrases").body()
        assertEquals(listOf(first), all)
    }

    @Test
    fun `list preserves insertion order`() = appTest {
        val client = jsonClient()
        listOf("☕" to "Coffee break", "🌙" to "Good night", "🚿" to "Take a shower!").forEach { (emoji, phrase) ->
            client.post("/api/v1/phrases") {
                jsonBody("""{"emoji":"$emoji","phrase":"$phrase"}""")
            }
        }

        val all: List<EmojiPhrase> = client.get("/api/v1/phrases").body()

        assertEquals(listOf(1, 2, 3), all.map { it.id })
        assertEquals(listOf("☕", "🌙", "🚿"), all.map { it.emoji })
    }

    @Test
    fun `get by id returns the phrase`() = appTest {
        val client = jsonClient()
        val created: EmojiPhrase = client.post("/api/v1/phrases") {
            jsonBody("""{"emoji":"☕","phrase":"Coffee break"}""")
        }.body()

        val fetched: EmojiPhrase = client.get("/api/v1/phrases/${created.id}").body()

        assertEquals(created, fetched)
    }

    @Test
    fun `get unknown id returns 404`() = appTest {
        val response = jsonClient().get("/api/v1/phrases/999")

        assertEquals(HttpStatusCode.NotFound, response.status)
        val error: ErrorResponse = response.body()
        assertEquals("not_found", error.error)
        assertContains(error.message, "999")
    }

    @Test
    fun `delete removes the phrase and is idempotent from the client perspective`() = appTest {
        val client = jsonClient()
        val created: EmojiPhrase = client.post("/api/v1/phrases") {
            jsonBody("""{"emoji":"🌙","phrase":"Good night"}""")
        }.body()

        assertEquals(HttpStatusCode.NoContent, client.delete("/api/v1/phrases/${created.id}").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/phrases/${created.id}").status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/v1/phrases/${created.id}").status)

        val all: List<EmojiPhrase> = client.get("/api/v1/phrases").body()
        assertTrue(all.isEmpty())
    }

    @Test
    fun `invalid ids return 400`() = appTest {
        val client = jsonClient()

        listOf("abc", "0", "-1").forEach { id ->
            val response = client.get("/api/v1/phrases/$id")
            assertEquals(HttpStatusCode.BadRequest, response.status, "GET id=$id")

            val error: ErrorResponse = response.body()
            assertEquals("validation_error", error.error, "GET id=$id")
            assertContains(error.message, "positive integer", message = "GET id=$id")
        }
    }

    @Test
    fun `blank fields return 400 listing every problem`() = appTest {
        val response = jsonClient().post("/api/v1/phrases") {
            jsonBody("""{"emoji":" ","phrase":""}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error: ErrorResponse = response.body()
        assertEquals("validation_error", error.error)
        assertContains(error.message, "emoji must not be blank")
        assertContains(error.message, "phrase must not be blank")
    }

    @Test
    fun `missing fields return 400`() = appTest {
        val response = jsonClient().post("/api/v1/phrases") {
            jsonBody("""{}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error: ErrorResponse = response.body()
        assertEquals("validation_error", error.error)
    }

    @Test
    fun `field length boundaries are enforced`() = appTest {
        val client = jsonClient()
        val phrase = """{"emoji":"%s","phrase":"ok"}"""

        val atLimit = client.post("/api/v1/phrases") {
            jsonBody(phrase.format("e".repeat(32)))
        }
        assertEquals(HttpStatusCode.Created, atLimit.status)

        val overLimit = client.post("/api/v1/phrases") {
            jsonBody(phrase.format("e".repeat(33)))
        }
        assertEquals(HttpStatusCode.BadRequest, overLimit.status)
        assertContains(overLimit.body<ErrorResponse>().message, "at most 32")

        val phraseLimit = client.post("/api/v1/phrases") {
            jsonBody("""{"emoji":"x","phrase":"${"p".repeat(280)}"}""")
        }
        assertEquals(HttpStatusCode.Created, phraseLimit.status)

        val phraseOverLimit = client.post("/api/v1/phrases") {
            jsonBody("""{"emoji":"y","phrase":"${"p".repeat(281)}"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, phraseOverLimit.status)
        assertContains(phraseOverLimit.body<ErrorResponse>().message, "at most 280")
    }

    @Test
    fun `unknown json fields are ignored`() = appTest {
        val response = jsonClient().post("/api/v1/phrases") {
            jsonBody("""{"emoji":"☕","phrase":"Coffee break","unexpected":true}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `malformed json returns 400`() = appTest {
        val response = jsonClient().post("/api/v1/phrases") {
            jsonBody("""{"emoji":""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error: ErrorResponse = response.body()
        assertEquals("invalid_body", error.error)
    }

    @Test
    fun `unsupported content type returns 400`() = appTest {
        val response = jsonClient().post("/api/v1/phrases") {
            contentType(ContentType.Text.Plain)
            setBody("""{"emoji":"☕","phrase":"Coffee break"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error: ErrorResponse = response.body()
        assertEquals("invalid_body", error.error)
    }

    @Test
    fun `unsupported method returns 405`() = appTest {
        val response = jsonClient().put("/api/v1/phrases")

        assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
    }

    @Test
    fun `unexpected failures return a generic 500 without leaking details`() {
        val broken = object : Repository by InMemoryRepository() {
            override suspend fun phrases(): List<EmojiPhrase> =
                throw IllegalStateException("database password=hunter2")
        }

        appTest(broken) {
            val response = jsonClient().get("/api/v1/phrases")

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            val error: ErrorResponse = response.body()
            assertEquals("internal_error", error.error)
            assertEquals("Unexpected server error", error.message)
            assertFalse(response.bodyAsText().contains("hunter2"))
        }
    }
}

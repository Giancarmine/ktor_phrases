package com.ges

import com.ges.repository.InMemoryRepository
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WebappTest {

    @Test
    fun `home returns the endpoint index`() = appTest {
        val response = client.get("/")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Text.Plain, response.contentType()?.withoutParameters())

        val body = response.bodyAsText()
        assertContains(body, "POST   /api/v1/phrases")
        assertContains(body, "DELETE /api/v1/phrases/{id}")
        assertContains(body, "GET    /docs")
    }

    @Test
    fun `swagger ui is served`() = appTest {
        val response = client.get("/docs")

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "Swagger")
    }

    @Test
    fun `openapi document is served`() = appTest {
        val response = client.get("/docs/openapi.yaml")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "openapi: 3.0.3")
        assertContains(body, "/api/v1/phrases")
    }

    @Test
    fun `about links to the repository`() = appTest {
        val response = client.get("/about")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Text.Plain, response.contentType()?.withoutParameters())
        assertContains(response.bodyAsText(), "github.com/Giancarmine/ktor_phrases")
    }

    @Test
    fun `html view shows the empty state`() = appTest {
        val response = client.get("/phrases")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Text.Html, response.contentType()?.withoutParameters())
        assertContains(response.bodyAsText(), "No phrases yet")
    }

    @Test
    fun `html view renders phrases`() {
        val repository = InMemoryRepository()
        appTest(repository) {
            repository.add("🚿", "Take a shower!")

            val body = client.get("/phrases").bodyAsText()

            assertContains(body, "<span>🚿</span>")
            assertContains(body, "Take a shower!")
            assertContains(body, "#1")
        }
    }

    @Test
    fun `html view escapes emoji and phrase values`() {
        val repository = InMemoryRepository()
        appTest(repository) {
            repository.add("<b>emoji</b>", "<script>alert('xss')</script>")

            val body = client.get("/phrases").bodyAsText()

            assertContains(body, "&lt;b&gt;emoji&lt;/b&gt;")
            assertContains(body, "&lt;script&gt;alert(")
            assertContains(body, "&lt;/script&gt;")
            assertFalse(body.contains("<script>alert('xss')</script>"))
        }
    }
}

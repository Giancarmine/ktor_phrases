package com.ges

import com.ges.model.EmojiPhrase
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationModuleTest {

    @Test
    fun `module reads environment configuration`() = testApplication {
        environment {
            config = MapApplicationConfig(
                "app.storage" to "memory",
                "app.pageSize" to "1",
                "app.maxPageSize" to "2",
            )
        }
        application { module() }

        val client = jsonClient()
        repeat(2) { index ->
            client.post("/api/v1/phrases") {
                jsonBody("""{"emoji":"e$index","phrase":"p$index"}""")
            }
        }

        val response = client.get("/api/v1/phrases")

        assertEquals("2", response.headers["X-Total-Count"])
        assertEquals("1", response.headers["X-Limit"])
        assertEquals(1, response.body<List<EmojiPhrase>>().size)
    }

    @Test
    fun `sqlite module persists data across restarts`() {
        val directory = createTempDirectory("ktor-phrases-module")
        val databasePath = directory.resolve("phrases.db").toString()
        try {
            testApplication {
                environment { config = sqliteConfig(databasePath) }
                application { module() }

                val response = jsonClient().post("/api/v1/phrases") {
                    jsonBody("""{"emoji":"☕","phrase":"Coffee break"}""")
                }

                assertEquals(HttpStatusCode.Created, response.status)
            }

            testApplication {
                environment { config = sqliteConfig(databasePath) }
                application { module() }

                val client = jsonClient()
                val response = client.get("/api/v1/phrases")

                assertEquals("1", response.headers["X-Total-Count"])
                assertEquals("Coffee break", response.body<List<EmojiPhrase>>().single().phrase)
                assertEquals(HttpStatusCode.OK, client.get("/ready").status)
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun sqliteConfig(databasePath: String) = MapApplicationConfig(
        "app.storage" to "sqlite",
        "app.databasePath" to databasePath,
    )
}

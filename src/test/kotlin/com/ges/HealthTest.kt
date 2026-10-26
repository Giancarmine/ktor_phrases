package com.ges

import com.ges.model.HealthResponse
import com.ges.repository.InMemoryRepository
import com.ges.repository.Repository
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HealthTest {

    @Test
    fun `health reports ok`() = appTest {
        val response = jsonClient().get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(HealthResponse(status = "ok"), response.body())
    }

    @Test
    fun `ready reports ready when storage is reachable`() = appTest {
        val response = jsonClient().get("/ready")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(HealthResponse(status = "ready"), response.body())
    }

    @Test
    fun `ready reports unavailable when storage fails without leaking details`() {
        val broken = object : Repository by InMemoryRepository() {
            override suspend fun count(): Int = throw IllegalStateException("database password=hunter2")
        }

        appTest(broken) {
            val response = jsonClient().get("/ready")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertContains(body, "\"status\": \"unavailable\"")
            assertFalse(body.contains("hunter2"))
        }
    }
}

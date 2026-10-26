package com.ges.config

import io.ktor.server.config.MapApplicationConfig
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppConfigTest {

    @Test
    fun `defaults are used when settings are absent`() {
        assertEquals(AppConfig(), AppConfig.from(MapApplicationConfig()))
    }

    @Test
    fun `settings are read from configuration`() {
        val config = AppConfig.from(
            MapApplicationConfig(
                "app.storage" to "sqlite",
                "app.databasePath" to "/tmp/phrases.db",
                "app.pageSize" to "5",
                "app.maxPageSize" to "10",
                "app.maxEmojiLength" to "8",
                "app.maxPhraseLength" to "80",
            ),
        )

        assertEquals(Storage.SQLITE, config.storage)
        assertEquals("/tmp/phrases.db", config.databasePath)
        assertEquals(5, config.pageSize)
        assertEquals(10, config.maxPageSize)
        assertEquals(8, config.maxEmojiLength)
        assertEquals(80, config.maxPhraseLength)
    }

    @Test
    fun `storage is case insensitive`() {
        val config = AppConfig.from(MapApplicationConfig("app.storage" to "SQLite"))

        assertEquals(Storage.SQLITE, config.storage)
    }

    @Test
    fun `unknown storage is rejected`() {
        val error = assertFailsWith<IllegalArgumentException> {
            AppConfig.from(MapApplicationConfig("app.storage" to "redis"))
        }

        assertContains(error.message.orEmpty(), "app.storage must be one of")
    }

    @Test
    fun `invalid numbers are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            AppConfig.from(MapApplicationConfig("app.pageSize" to "many"))
        }
        assertFailsWith<IllegalArgumentException> {
            AppConfig.from(MapApplicationConfig("app.pageSize" to "0"))
        }
        assertFailsWith<IllegalArgumentException> {
            AppConfig.from(MapApplicationConfig("app.pageSize" to "10", "app.maxPageSize" to "5"))
        }
        assertFailsWith<IllegalArgumentException> {
            AppConfig.from(MapApplicationConfig("app.maxEmojiLength" to "0"))
        }
        assertFailsWith<IllegalArgumentException> {
            AppConfig.from(MapApplicationConfig("app.maxPhraseLength" to "0"))
        }
    }

    @Test
    fun `sqlite requires a database path`() {
        assertFailsWith<IllegalArgumentException> {
            AppConfig(storage = Storage.SQLITE, databasePath = "")
        }
    }
}

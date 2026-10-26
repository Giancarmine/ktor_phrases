package com.ges

import com.ges.config.AppConfig
import com.ges.config.Storage
import com.ges.repository.InMemoryRepository
import com.ges.repository.SqliteRepository
import kotlin.test.Test
import kotlin.test.assertIs

class RepositoryFactoryTest {

    @Test
    fun `memory configuration creates an in-memory repository`() {
        assertIs<InMemoryRepository>(createRepository(AppConfig(storage = Storage.MEMORY)))
    }

    @Test
    fun `sqlite configuration creates a sqlite repository`() {
        val repository = createRepository(
            AppConfig(storage = Storage.SQLITE, databasePath = ":memory:"),
        )

        try {
            assertIs<SqliteRepository>(repository)
        } finally {
            (repository as AutoCloseable).close()
        }
    }
}

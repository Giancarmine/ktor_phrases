package com.ges.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqliteRepositoryTest {

    @Test
    fun `add assigns ids and deduplicates identical pairs`() = runTest {
        withRepository { repository ->
            val first = repository.add("☕", "Coffee break")
            val duplicate = repository.add("☕", "Coffee break")
            val second = repository.add("🌙", "Good night")

            assertTrue(first.created)
            assertFalse(duplicate.created)
            assertEquals(first.phrase, duplicate.phrase)
            assertEquals(1, first.phrase.id)
            assertEquals(2, second.phrase.id)
            assertEquals(2, repository.count())
        }
    }

    @Test
    fun `list supports pagination and preserves order`() = runTest {
        withRepository { repository ->
            repeat(5) { index -> repository.add("e$index", "p$index") }

            assertEquals(listOf(2, 3), repository.phrases(offset = 1, limit = 2).map { it.id })
            assertEquals(5, repository.count())
            assertEquals(listOf(4, 5), repository.phrases(offset = 3, limit = 10).map { it.id })
        }
    }

    @Test
    fun `page returns items and total from one transaction`() = runTest {
        withRepository { repository ->
            repeat(5) { index -> repository.add("e$index", "p$index") }

            val page = repository.page(offset = 1, limit = 2)

            assertEquals(5, page.total)
            assertEquals(listOf(2, 3), page.items.map { it.id })
        }
    }

    @Test
    fun `page past the end still returns the total`() = runTest {
        withRepository { repository ->
            repeat(2) { index -> repository.add("e$index", "p$index") }

            val page = repository.page(offset = 10, limit = 2)

            assertEquals(2, page.total)
            assertTrue(page.items.isEmpty())
        }
    }

    @Test
    fun `get remove and clear update the database`() = runTest {
        withRepository { repository ->
            val added = repository.add("🚿", "Take a shower!")

            assertEquals(added.phrase, repository.phrase(added.phrase.id))
            assertTrue(repository.remove(added.phrase.id))
            assertFalse(repository.remove(added.phrase.id))
            assertNull(repository.phrase(added.phrase.id))

            repository.add("☕", "Coffee break")
            repository.add("🌙", "Good night")
            repository.clear()

            assertEquals(0, repository.count())
            assertTrue(repository.phrases().isEmpty())
        }
    }

    @Test
    fun `data survives reconnecting to the same file`() = runTest {
        val directory = createTempDirectory("ktor-phrases-test")
        val databasePath = directory.resolve("phrases.db").toString()
        try {
            withRepository(databasePath) { repository ->
                assertTrue(repository.add("☕", "Coffee break").created)
            }

            withRepository(databasePath) { repository ->
                assertEquals(1, repository.count())
                assertEquals("Coffee break", repository.phrase(1)?.phrase)
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `concurrent adds keep unique ids`() = runTest {
        withRepository { repository ->
            val results = (1..50)
                .map { index -> async(Dispatchers.Default) { repository.add("e$index", "p$index") } }
                .awaitAll()

            assertEquals(50, results.map { it.phrase.id }.toSet().size)
            assertEquals(50, repository.count())
        }
    }

    @Test
    fun `concurrent identical adds create exactly one entry`() = runTest {
        withRepository { repository ->
            val results = (1..50)
                .map { async(Dispatchers.Default) { repository.add("🚿", "Take a shower!") } }
                .awaitAll()

            assertEquals(1, results.count { it.created })
            assertEquals(1, repository.count())
        }
    }

    private suspend fun withRepository(
        databasePath: String = ":memory:",
        block: suspend (SqliteRepository) -> Unit,
    ) {
        val repository = SqliteRepository(databasePath)
        try {
            block(repository)
        } finally {
            repository.close()
        }
    }
}

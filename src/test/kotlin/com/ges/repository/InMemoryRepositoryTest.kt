package com.ges.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryRepositoryTest {

    @Test
    fun `add assigns sequential ids`() = runTest {
        val repository = InMemoryRepository()

        val first = repository.add("☕", "Coffee break")
        val second = repository.add("🌙", "Good night")

        assertTrue(first.created)
        assertTrue(second.created)
        assertEquals(1, first.phrase.id)
        assertEquals(2, second.phrase.id)
        assertEquals(listOf(1, 2), repository.phrases().map { it.id })
    }

    @Test
    fun `adding an identical pair returns the existing phrase`() = runTest {
        val repository = InMemoryRepository()

        val first = repository.add("🚿", "Take a shower!")
        val duplicate = repository.add("🚿", "Take a shower!")

        assertTrue(first.created)
        assertFalse(duplicate.created)
        assertEquals(first.phrase, duplicate.phrase)
        assertEquals(1, repository.phrases().size)
    }

    @Test
    fun `phrases returns a snapshot`() = runTest {
        val repository = InMemoryRepository()
        repository.add("☕", "Coffee break")

        val snapshot = repository.phrases()
        repository.add("🌙", "Good night")

        assertEquals(1, snapshot.size)
        assertEquals(2, repository.phrases().size)
    }

    @Test
    fun `phrase returns null for an unknown id`() = runTest {
        val repository = InMemoryRepository()

        assertNull(repository.phrase(42))
    }

    @Test
    fun `remove deletes the phrase and reports whether it existed`() = runTest {
        val repository = InMemoryRepository()
        val added = repository.add("☕", "Coffee break")

        assertTrue(repository.remove(added.phrase.id))
        assertFalse(repository.remove(added.phrase.id))
        assertNull(repository.phrase(added.phrase.id))
        assertTrue(repository.phrases().isEmpty())
    }

    @Test
    fun `clear removes every phrase`() = runTest {
        val repository = InMemoryRepository()
        repository.add("☕", "Coffee break")
        repository.add("🌙", "Good night")

        repository.clear()

        assertTrue(repository.phrases().isEmpty())
        assertNull(repository.phrase(1))
    }

    @Test
    fun `concurrent adds produce unique ids and keep every entry`() = runTest {
        val repository = InMemoryRepository()

        val results = (1..200)
            .map { index -> async(Dispatchers.Default) { repository.add("e$index", "p$index") } }
            .awaitAll()

        assertTrue(results.all { it.created })
        assertEquals(200, results.map { it.phrase.id }.toSet().size)
        assertEquals(200, repository.phrases().size)
    }

    @Test
    fun `concurrent identical adds create exactly one entry`() = runTest {
        val repository = InMemoryRepository()

        val results = (1..100)
            .map { async(Dispatchers.Default) { repository.add("🚿", "Take a shower!") } }
            .awaitAll()

        assertEquals(1, results.count { it.created })
        assertEquals(1, results.map { it.phrase.id }.toSet().size)
        assertEquals(1, repository.phrases().size)
    }
}

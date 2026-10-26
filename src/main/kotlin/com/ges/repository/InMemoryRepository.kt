package com.ges.repository

import com.ges.model.EmojiPhrase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe, process-local store. Data is lost when the server restarts.
 */
class InMemoryRepository : Repository {
    private val mutex = Mutex()
    private val phrases = LinkedHashMap<Int, EmojiPhrase>()
    private var nextId = 0

    override suspend fun add(emoji: String, phrase: String): AddResult = mutex.withLock {
        phrases.values.firstOrNull { it.emoji == emoji && it.phrase == phrase }
            ?.let { return@withLock AddResult(phrase = it, created = false) }

        val stored = EmojiPhrase(id = ++nextId, emoji = emoji, phrase = phrase)
        phrases[stored.id] = stored
        AddResult(phrase = stored, created = true)
    }

    override suspend fun phrase(id: Int): EmojiPhrase? = mutex.withLock { phrases[id] }

    override suspend fun phrases(): List<EmojiPhrase> = mutex.withLock { phrases.values.toList() }

    override suspend fun remove(id: Int): Boolean = mutex.withLock { phrases.remove(id) != null }

    override suspend fun clear() {
        mutex.withLock { phrases.clear() }
    }
}

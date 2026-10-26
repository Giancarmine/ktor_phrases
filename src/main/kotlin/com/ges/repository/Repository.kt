package com.ges.repository

import com.ges.model.EmojiPhrase

/**
 * Result of an add operation. [created] is false when an identical emoji/phrase pair already existed.
 */
data class AddResult(val phrase: EmojiPhrase, val created: Boolean)

interface Repository {
    suspend fun add(emoji: String, phrase: String): AddResult
    suspend fun phrase(id: Int): EmojiPhrase?
    suspend fun phrases(): List<EmojiPhrase>
    suspend fun remove(id: Int): Boolean
    suspend fun clear()
}

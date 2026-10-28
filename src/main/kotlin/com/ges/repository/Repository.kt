package com.ges.repository

import com.ges.model.EmojiPhrase

/**
 * Result of an add operation. [created] is false when an identical emoji/phrase pair already existed.
 */
data class AddResult(val phrase: EmojiPhrase, val created: Boolean)

/**
 * A page of phrases plus the total count observed in the same snapshot.
 */
data class Page(val items: List<EmojiPhrase>, val total: Int)

interface Repository {
    suspend fun add(emoji: String, phrase: String): AddResult
    suspend fun phrase(id: Int): EmojiPhrase?

    /**
     * Returns one consistent snapshot: [Page.total] and [Page.items] cannot disagree because of a
     * concurrent write.
     */
    suspend fun page(offset: Int = 0, limit: Int = Int.MAX_VALUE): Page

    suspend fun phrases(offset: Int = 0, limit: Int = Int.MAX_VALUE): List<EmojiPhrase> =
        page(offset, limit).items

    suspend fun count(): Int
    suspend fun remove(id: Int): Boolean
    suspend fun clear()
}

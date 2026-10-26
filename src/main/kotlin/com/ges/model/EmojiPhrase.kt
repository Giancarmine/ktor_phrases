package com.ges.model

import kotlinx.serialization.Serializable

@Serializable
data class EmojiPhrase(val id: Int, val emoji: String, val phrase: String)

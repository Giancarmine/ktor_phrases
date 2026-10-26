package com.ges.config

import io.ktor.server.config.ApplicationConfig

enum class Storage {
    MEMORY,
    SQLITE,
}

/**
 * Application settings read from `application.conf` with environment-variable overrides.
 */
data class AppConfig(
    val storage: Storage = Storage.MEMORY,
    val databasePath: String = "data/phrases.db",
    val pageSize: Int = 20,
    val maxPageSize: Int = 100,
    val maxEmojiLength: Int = 32,
    val maxPhraseLength: Int = 280,
) {
    init {
        require(pageSize > 0) { "app.pageSize must be greater than 0" }
        require(maxPageSize >= pageSize) { "app.maxPageSize must be greater than or equal to app.pageSize" }
        require(maxEmojiLength > 0) { "app.maxEmojiLength must be greater than 0" }
        require(maxPhraseLength > 0) { "app.maxPhraseLength must be greater than 0" }
        require(storage != Storage.SQLITE || databasePath.isNotBlank()) {
            "app.databasePath must not be blank when app.storage is sqlite"
        }
    }

    companion object {
        fun from(config: ApplicationConfig): AppConfig {
            val storageValue = config.string("app.storage", "memory")
            val storage = Storage.entries.firstOrNull { it.name.equals(storageValue, ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "app.storage must be one of: ${Storage.entries.joinToString { it.name.lowercase() }} (was '$storageValue')",
                )

            return AppConfig(
                storage = storage,
                databasePath = config.string("app.databasePath", "data/phrases.db"),
                pageSize = config.int("app.pageSize", 20),
                maxPageSize = config.int("app.maxPageSize", 100),
                maxEmojiLength = config.int("app.maxEmojiLength", 32),
                maxPhraseLength = config.int("app.maxPhraseLength", 280),
            )
        }
    }
}

private fun ApplicationConfig.string(path: String, default: String): String =
    propertyOrNull(path)?.getString()?.takeIf { it.isNotBlank() } ?: default

private fun ApplicationConfig.int(path: String, default: Int): Int {
    val raw = propertyOrNull(path)?.getString() ?: return default
    return raw.toIntOrNull() ?: throw IllegalArgumentException("$path must be an integer (was '$raw')")
}

package com.ges.repository

import com.ges.model.EmojiPhrase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement

/**
 * SQLite-backed store. A single guarded connection is enough for this demo and keeps
 * transactions ordered; swap in a connection pool if throughput matters.
 *
 * Pass `:memory:` as [databasePath] for an ephemeral database (used by tests).
 */
class SqliteRepository(databasePath: String) : Repository, AutoCloseable {
    private val mutex = Mutex()
    private val connection: Connection = open(databasePath)

    init {
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS phrases (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    emoji TEXT NOT NULL,
                    phrase TEXT NOT NULL,
                    UNIQUE (emoji, phrase)
                )
                """.trimIndent(),
            )
        }
    }

    override suspend fun add(emoji: String, phrase: String): AddResult = mutex.withLock {
        find(emoji, phrase)?.let { return@withLock AddResult(phrase = it, created = false) }

        val id = connection.prepareStatement(
            "INSERT INTO phrases(emoji, phrase) VALUES (?, ?)",
            Statement.RETURN_GENERATED_KEYS,
        ).use { statement ->
            statement.setString(1, emoji)
            statement.setString(2, phrase)
            statement.executeUpdate()
            statement.generatedKeys.use { keys ->
                check(keys.next()) { "SQLite did not return a generated id" }
                keys.getInt(1)
            }
        }
        AddResult(phrase = EmojiPhrase(id = id, emoji = emoji, phrase = phrase), created = true)
    }

    override suspend fun phrase(id: Int): EmojiPhrase? = mutex.withLock {
        connection.prepareStatement("SELECT id, emoji, phrase FROM phrases WHERE id = ?").use { statement ->
            statement.setInt(1, id)
            statement.executeQuery().use { result -> if (result.next()) result.toPhrase() else null }
        }
    }

    override suspend fun phrases(offset: Int, limit: Int): List<EmojiPhrase> = mutex.withLock {
        connection.prepareStatement(
            "SELECT id, emoji, phrase FROM phrases ORDER BY id LIMIT ? OFFSET ?",
        ).use { statement ->
            statement.setInt(1, limit)
            statement.setInt(2, offset)
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) add(result.toPhrase())
                }
            }
        }
    }

    override suspend fun count(): Int = mutex.withLock {
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM phrases").use { result ->
                result.next()
                result.getInt(1)
            }
        }
    }

    override suspend fun remove(id: Int): Boolean = mutex.withLock {
        connection.prepareStatement("DELETE FROM phrases WHERE id = ?").use { statement ->
            statement.setInt(1, id)
            statement.executeUpdate() > 0
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            connection.createStatement().use { statement ->
                statement.executeUpdate("DELETE FROM phrases")
            }
        }
    }

    override fun close() {
        connection.close()
    }

    private fun find(emoji: String, phrase: String): EmojiPhrase? =
        connection.prepareStatement(
            "SELECT id, emoji, phrase FROM phrases WHERE emoji = ? AND phrase = ?",
        ).use { statement ->
            statement.setString(1, emoji)
            statement.setString(2, phrase)
            statement.executeQuery().use { result -> if (result.next()) result.toPhrase() else null }
        }

    private fun ResultSet.toPhrase() = EmojiPhrase(
        id = getInt("id"),
        emoji = getString("emoji"),
        phrase = getString("phrase"),
    )

    private companion object {
        fun open(path: String): Connection {
            require(path.isNotBlank()) { "databasePath must not be blank" }
            val url = if (path.startsWith("jdbc:")) {
                path
            } else {
                if (path != ":memory:") {
                    File(path).absoluteFile.parentFile?.mkdirs()
                }
                "jdbc:sqlite:$path"
            }
            return DriverManager.getConnection(url)
        }
    }
}

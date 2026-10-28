package com.ges.repository

import com.ges.model.EmojiPhrase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * SQLite-backed store. JDBC is blocking, so every operation runs on [Dispatchers.IO] and is
 * serialized by a lock. A single connection is enough for this demo; add a pool if write
 * throughput matters.
 *
 * WAL is enabled for file databases so readers do not block writers when another process opens
 * the same file. Pass `:memory:` as [databasePath] for an ephemeral database (used by tests).
 */
class SqliteRepository(databasePath: String) : Repository, AutoCloseable {
    private val lock = ReentrantLock()
    private val connection: Connection = open(databasePath)

    @Volatile
    private var closed = false

    init {
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA journal_mode = WAL")
            statement.execute("PRAGMA synchronous = NORMAL")
            statement.execute("PRAGMA busy_timeout = 5000")
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

    override suspend fun add(emoji: String, phrase: String): AddResult = withConnection {
        find(emoji, phrase)?.let { return@withConnection AddResult(phrase = it, created = false) }

        val id = prepareStatement(
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

    override suspend fun phrase(id: Int): EmojiPhrase? = withConnection {
        prepareStatement("SELECT id, emoji, phrase FROM phrases WHERE id = ?").use { statement ->
            statement.setInt(1, id)
            statement.executeQuery().use { result -> if (result.next()) result.toPhrase() else null }
        }
    }

    override suspend fun page(offset: Int, limit: Int): Page = withConnection {
        val previousAutoCommit = autoCommit
        autoCommit = false
        try {
            val total = countRows()
            val items = selectPage(offset, limit)
            commit()
            Page(items = items, total = total)
        } catch (cause: Exception) {
            rollback()
            throw cause
        } finally {
            autoCommit = previousAutoCommit
        }
    }

    override suspend fun count(): Int = withConnection { countRows() }

    override suspend fun remove(id: Int): Boolean = withConnection {
        prepareStatement("DELETE FROM phrases WHERE id = ?").use { statement ->
            statement.setInt(1, id)
            statement.executeUpdate() > 0
        }
    }

    override suspend fun clear() {
        withConnection {
            createStatement().use { statement ->
                statement.executeUpdate("DELETE FROM phrases")
            }
        }
    }

    override fun close() {
        lock.withLock {
            if (!closed) {
                closed = true
                connection.close()
            }
        }
    }

    private suspend fun <T> withConnection(block: Connection.() -> T): T =
        withContext(Dispatchers.IO) {
            lock.withLock {
                check(!closed) { "SqliteRepository is closed" }
                connection.block()
            }
        }

    private fun Connection.find(emoji: String, phrase: String): EmojiPhrase? =
        prepareStatement(
            "SELECT id, emoji, phrase FROM phrases WHERE emoji = ? AND phrase = ?",
        ).use { statement ->
            statement.setString(1, emoji)
            statement.setString(2, phrase)
            statement.executeQuery().use { result -> if (result.next()) result.toPhrase() else null }
        }

    private fun Connection.countRows(): Int =
        createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM phrases").use { result ->
                result.next()
                result.getInt(1)
            }
        }

    private fun Connection.selectPage(offset: Int, limit: Int): List<EmojiPhrase> =
        prepareStatement(
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

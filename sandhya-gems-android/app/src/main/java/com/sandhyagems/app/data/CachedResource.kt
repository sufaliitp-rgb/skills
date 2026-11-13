package com.sandhyagems.app.data

import com.sandhyagems.app.util.AppExecutors
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed class Outcome<out T> {
    /**
     * @param isFinal false when this is saved data and a fresher copy is still being downloaded.
     * @param offline true when the download failed and this is the last saved copy.
     */
    data class Success<T>(val value: T, val isFinal: Boolean, val offline: Boolean = false) : Outcome<T>()

    data class Failure(val message: String) : Outcome<Nothing>()
}

/**
 * One piece of store data, kept in memory and on disk.
 *
 * [load] answers straight away with whatever is saved, then refreshes from the
 * network when the saved copy is older than [ttlMs]. So the app opens instantly
 * and still works with a weak or missing connection.
 *
 * All public methods must be called on the main thread; callbacks arrive there too.
 */
class CachedResource<T : Any>(
    private val file: File,
    private val ttlMs: Long,
    private val fetch: () -> String,
    private val parse: (String) -> T,
) {
    private var value: T? = null
    private var loadedAt = 0L
    private var running = false
    private val waiters = ArrayList<(Outcome<T>) -> Unit>()

    fun peek(): T? = value

    /** Puts a value in memory as if it had just been downloaded. Used by tests. */
    fun prime(v: T) {
        value = v
        loadedAt = System.currentTimeMillis()
    }

    fun load(force: Boolean = false, callback: (Outcome<T>) -> Unit) {
        val inMemory = value
        val fresh = inMemory != null && isFresh(loadedAt)
        if (inMemory != null) callback(Outcome.Success(inMemory, isFinal = fresh && !force))
        if (fresh && !force) return

        waiters.add(callback)
        if (running) return
        running = true
        val hadMemory = inMemory != null

        AppExecutors.io.execute {
            var diskFresh = false
            if (!hadMemory) {
                val saved = readDisk()
                if (saved != null) {
                    val savedAt = file.lastModified()
                    diskFresh = isFresh(savedAt)
                    val final = diskFresh && !force
                    AppExecutors.main.post {
                        value = saved
                        loadedAt = savedAt
                        deliver(Outcome.Success(saved, isFinal = final), done = final)
                    }
                }
            }
            if (diskFresh && !force) return@execute

            try {
                val raw = fetch()
                val parsed = parse(raw)
                writeDisk(raw)
                AppExecutors.main.post {
                    value = parsed
                    loadedAt = System.currentTimeMillis()
                    deliver(Outcome.Success(parsed, isFinal = true), done = true)
                }
            } catch (e: Exception) {
                AppExecutors.main.post {
                    val saved = value
                    val outcome: Outcome<T> =
                        if (saved != null) Outcome.Success(saved, isFinal = true, offline = true)
                        else Outcome.Failure(describe(e))
                    deliver(outcome, done = true)
                }
            }
        }
    }

    private fun deliver(outcome: Outcome<T>, done: Boolean) {
        val targets = ArrayList(waiters)
        if (done) {
            waiters.clear()
            running = false
        }
        targets.forEach { it(outcome) }
    }

    private fun isFresh(time: Long): Boolean {
        val age = System.currentTimeMillis() - time
        return age in 0 until ttlMs
    }

    private fun readDisk(): T? = try {
        if (file.exists()) parse(file.readText()) else null
    } catch (e: Exception) {
        file.delete()
        null
    }

    private fun writeDisk(raw: String) {
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(raw)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            // The cache is a convenience; failing to save it must not break the screen.
        }
    }

    private fun describe(e: Exception): String = when (e) {
        is UnknownHostException, is ConnectException, is SocketTimeoutException ->
            "Please check your internet connection and try again."
        is HttpException -> "The store is not responding right now (error ${e.code}). Please try again in a moment."
        else -> "Something went wrong while loading. Please try again."
    }
}

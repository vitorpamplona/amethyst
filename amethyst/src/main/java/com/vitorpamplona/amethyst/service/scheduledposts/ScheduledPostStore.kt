/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.service.scheduledposts

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class ScheduledPostStore(
    private val storageFile: File,
    private val nowSec: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val mapper =
        jacksonObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val mutex = Mutex()
    private var loaded = false
    private var posts: MutableList<ScheduledPost> = mutableListOf()

    private val _flow = MutableStateFlow<List<ScheduledPost>>(emptyList())

    /** Live snapshot of all stored posts. Updated on every mutation. */
    val flow: StateFlow<List<ScheduledPost>> = _flow.asStateFlow()

    suspend fun add(post: ScheduledPost) =
        mutex.withLock {
            ensureLoaded()
            posts.add(post)
            persist()
        }

    suspend fun cancel(id: String): Boolean =
        mutex.withLock {
            ensureLoaded()
            val now = nowSec()
            val updated = mutate(id) { it.copy(status = ScheduledPostStatus.CANCELLED, terminatedAtSec = now) }
            if (updated) persist()
            updated
        }

    suspend fun list(): List<ScheduledPost> =
        mutex.withLock {
            ensureLoaded()
            posts.toList()
        }

    suspend fun listFor(accountPubkey: String): List<ScheduledPost> =
        mutex.withLock {
            ensureLoaded()
            posts.filter { it.accountPubkey == accountPubkey }
        }

    /**
     * Atomically claim posts due at or before [nowSec]: each PENDING post with
     * publishAtSec <= now is flipped to PUBLISHING and returned. A concurrent
     * claim from another worker will see those posts as PUBLISHING and skip them.
     */
    suspend fun claimDuePosts(nowSec: Long): List<ScheduledPost> =
        mutex.withLock {
            ensureLoaded()
            val dueIds =
                posts
                    .filter { it.status == ScheduledPostStatus.PENDING && it.publishAtSec <= nowSec }
                    .map { it.id }
                    .toSet()
            if (dueIds.isEmpty()) return@withLock emptyList()
            dueIds.forEach { id ->
                mutate(id) {
                    it.copy(
                        status = ScheduledPostStatus.PUBLISHING,
                        lastAttemptAtSec = nowSec,
                        attemptCount = it.attemptCount + 1,
                    )
                }
            }
            persist()
            posts.filter { it.id in dueIds }
        }

    suspend fun markSent(id: String) =
        mutex.withLock {
            ensureLoaded()
            val now = nowSec()
            if (mutate(id) { it.copy(status = ScheduledPostStatus.SENT, lastError = null, terminatedAtSec = now) }) persist()
        }

    suspend fun markFailed(
        id: String,
        error: String?,
    ) = mutex.withLock {
        ensureLoaded()
        if (mutate(id) { it.copy(status = ScheduledPostStatus.FAILED, lastError = error) }) persist()
    }

    /**
     * Force a post to publish immediately by setting its publishAtSec to [nowSec]
     * and resetting status to PENDING. Handles two cases with one method:
     *  - PENDING (future-scheduled): user wants to push it out now
     *  - FAILED: user wants to retry
     * Caller is expected to enqueue ScheduledPostWorker.scheduleCatchUp() afterwards
     * so the worker picks it up promptly.
     */
    suspend fun publishNow(
        id: String,
        nowSec: Long = System.currentTimeMillis() / 1000,
    ): Boolean =
        mutex.withLock {
            ensureLoaded()
            val updated =
                mutate(id) {
                    it.copy(
                        publishAtSec = nowSec,
                        status = ScheduledPostStatus.PENDING,
                        lastError = null,
                        terminatedAtSec = null,
                    )
                }
            if (updated) persist()
            updated
        }

    /**
     * Remove every row owned by [accountPubkey]. Used when the user deletes
     * an account — the account's signed events should not linger. Returns the
     * number of rows removed; persists once if any rows matched.
     */
    suspend fun removeForAccount(accountPubkey: String): Int =
        mutex.withLock {
            ensureLoaded()
            val before = posts.size
            val removed = posts.removeAll { it.accountPubkey == accountPubkey }
            val count = before - posts.size
            if (removed) persist()
            count
        }

    /**
     * Revert a PUBLISHING claim back to PENDING (e.g. when the account is not
     * loaded at fire time, so we should retry on the next cycle rather than
     * marking the post failed permanently).
     */
    suspend fun releaseClaim(id: String) =
        mutex.withLock {
            ensureLoaded()
            val changed =
                mutate(id) {
                    if (it.status == ScheduledPostStatus.PUBLISHING) {
                        it.copy(status = ScheduledPostStatus.PENDING)
                    } else {
                        it
                    }
                }
            if (changed) persist()
        }

    private fun mutate(
        id: String,
        transform: (ScheduledPost) -> ScheduledPost,
    ): Boolean {
        val idx = posts.indexOfFirst { it.id == id }
        if (idx < 0) return false
        val before = posts[idx]
        val after = transform(before)
        if (after == before) return false
        posts[idx] = after
        return true
    }

    private fun ensureLoaded() {
        if (loaded) return
        posts =
            try {
                if (storageFile.exists() && storageFile.length() > 0) {
                    mapper.readValue<ScheduledPostFile>(storageFile).posts.toMutableList()
                } else {
                    mutableListOf()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load scheduled posts from $storageFile", e)
                mutableListOf()
            }
        loaded = true
        val purged = purgeStale(nowSec())
        _flow.value = posts.toList()
        if (purged) persist()
    }

    /**
     * Drop SENT rows older than [SENT_RETENTION_SEC] and CANCELLED rows older
     * than [CANCELLED_RETENTION_SEC]. Returns true if any row was removed.
     * FAILED rows are kept indefinitely so the user can still see and retry them;
     * PENDING / PUBLISHING rows are never purged.
     */
    private fun purgeStale(now: Long): Boolean {
        val before = posts.size
        posts.removeAll { post ->
            // terminatedAtSec is the post-PR field. Legacy rows lack it; fall back to
            // lastAttemptAtSec (set at SENT) and finally createdAtSec. The fallback
            // can purge old CANCELLED rows up to 30d earlier than intended on first
            // run after upgrade — self-healing once new rows are written.
            val age = now - (post.terminatedAtSec ?: post.lastAttemptAtSec ?: post.createdAtSec)
            when (post.status) {
                ScheduledPostStatus.SENT -> age > SENT_RETENTION_SEC
                ScheduledPostStatus.CANCELLED -> age > CANCELLED_RETENTION_SEC
                else -> false
            }
        }
        return posts.size < before
    }

    /**
     * Writes the snapshot to disk *while holding the data mutex*. This is a
     * deliberate tradeoff: moving the write outside the lock would require a
     * separate write-mutex (or a sequence number) to preserve write ordering
     * across concurrent mutations — otherwise an older snapshot can clobber a
     * newer one if the OS schedules the second write to finish first. For a
     * file that's a few KB and a single-process owner with infrequent writes,
     * holding the mutex across the rename is the simpler and correct choice.
     * Revisit if the store ever grows past a hundred rows or starts seeing
     * concurrent multi-writer pressure.
     */
    private fun persist() {
        val snapshot = posts.toList()
        _flow.value = snapshot
        storageFile.parentFile?.mkdirs()
        val tmp = File(storageFile.parentFile, storageFile.name + ".tmp")
        try {
            mapper.writeValue(tmp, ScheduledPostFile(version = 1, posts = snapshot))
            if (!tmp.renameTo(storageFile)) {
                storageFile.delete()
                if (!tmp.renameTo(storageFile)) {
                    Log.e(TAG, "Failed to rename $tmp to $storageFile")
                    tmp.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist scheduled posts to $storageFile", e)
            tmp.delete()
        }
    }

    companion object {
        private const val TAG = "ScheduledPostStore"
        const val FILE_NAME = "scheduled_posts.json"
        private const val SENT_RETENTION_SEC = 7L * 24 * 3600
        private const val CANCELLED_RETENTION_SEC = 30L * 24 * 3600
    }
}

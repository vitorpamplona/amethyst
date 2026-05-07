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
            val updated = mutate(id) { it.copy(status = ScheduledPostStatus.CANCELLED) }
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
            if (mutate(id) { it.copy(status = ScheduledPostStatus.SENT, lastError = null) }) persist()
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
                    )
                }
            if (updated) persist()
            updated
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
        if (after === before) return false
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
        _flow.value = posts.toList()
    }

    private fun persist() {
        val snapshot = posts.toList()
        _flow.value = snapshot
        val parent = storageFile.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()
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
    }
}

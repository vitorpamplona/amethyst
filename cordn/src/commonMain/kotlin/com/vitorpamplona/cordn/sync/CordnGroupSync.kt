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
package com.vitorpamplona.cordn.sync

import com.vitorpamplona.cordn.spec00Coordinator.CoordinatorClient
import com.vitorpamplona.cordn.spec00Coordinator.GroupMessage
import com.vitorpamplona.cordn.spec00Coordinator.PostedMessage

/**
 * Fetch-then-subscribe over a set of groups on one coordinator.
 *
 * ## Why the order matters
 *
 * `msg_sub_many` takes a cursor per group and delivers from there, so it looks
 * like subscribing alone would do. It would not: a subscription is a live
 * stream, and the catch-up it performs at open is bounded by what the
 * coordinator is willing to push in one go. Draining history with
 * `msg_fetch_many` first, then subscribing from the freshest cursor, is what
 * makes the two paths meet exactly once — which matters because MLS is a
 * sequence, not a set. A Commit processed out of order is a Commit that fails,
 * and the epoch it would have produced never arrives.
 *
 * Both paths feed [GroupInbox.accept], so a message seen twice — over the tail
 * of catch-up and the head of the subscription — is recognised the same way
 * either time.
 */
class CordnGroupSync(
    private val client: CoordinatorClient,
    /** Inbox per `gid`. The caller owns them, because they outlive a sync run. */
    private val inboxes: MutableMap<String, GroupInbox> = mutableMapOf(),
) {
    /** The inbox for [gid], created at cursor 0 if this is the first time. */
    fun inbox(gid: String): GroupInbox = inboxes.getOrPut(gid) { GroupInbox() }

    /** Resumes [gid] from a persisted cursor. */
    fun restore(
        gid: String,
        cursor: GroupCursor,
    ) {
        inbox(gid).cursor = cursor
    }

    /** The cursors to persist. */
    fun cursors(): Map<String, GroupCursor> = inboxes.mapValues { it.value.cursor }

    /**
     * Drains history for [gids] until every group is current.
     *
     * The coordinator decides page size, so "current" means a page came back
     * with nothing new for any group. [maxPages] bounds that loop: a group with
     * years of history should not block startup forever, and a coordinator that
     * kept returning the same page would otherwise spin.
     *
     * @return every message drained, in the order it was delivered.
     */
    suspend fun catchUp(
        gids: Collection<String>,
        maxPages: Int = DEFAULT_MAX_PAGES,
        onMessage: (String, Ingestion) -> Unit,
    ): Int {
        require(gids.isNotEmpty()) { "catchUp needs at least one group" }
        var delivered = 0

        repeat(maxPages) {
            val page = client.fetchMessages(gids.associateWith { inbox(it).cursor.afterOrNull() })
            // An empty page is the only honest "you are current" signal: the
            // coordinator does not say how much is left.
            if (page.isEmpty()) return delivered

            val before = gids.associateWith { inbox(it).cursor.lastCursor }
            page.forEach { message -> onMessage(message.gid, inbox(message.gid).accept(message)) }
            delivered += page.size

            // A page that moved no cursor means the coordinator is serving the
            // same records back. Stopping beats looping: the alternative is a
            // silent infinite fetch against a broken or hostile server.
            if (gids.none { inbox(it).cursor.lastCursor > (before[it] ?: 0) }) return delivered
        }
        return delivered
    }

    /**
     * Subscribes from each group's current cursor and delivers until the
     * coordinator closes the stream.
     *
     * Call [catchUp] first. Suspends for the life of the subscription, so give
     * it its own coroutine.
     */
    suspend fun subscribe(
        gids: Collection<String>,
        timeoutMs: Long,
        onMessage: (String, Ingestion) -> Unit,
    ) {
        require(gids.isNotEmpty()) { "subscribe needs at least one group" }
        client.subscribeMessages(
            cursors = gids.associateWith { inbox(it).cursor.afterOrNull() },
            timeoutMs = timeoutMs,
        ) { message ->
            onMessage(message.gid, inbox(message.gid).accept(message))
        }
    }

    /**
     * Posts a sealed application message and records it as ours.
     *
     * Recording it by the cursor the coordinator assigns is what stops the echo
     * being re-ingested as somebody else's message a moment later.
     */
    suspend fun postMessage(
        gid: String,
        sealedBase64: String,
    ): PostedMessage =
        client.postMessage(gid, sealedBase64).also {
            inbox(gid).recordOwnMessage(it.cursor)
        }

    /**
     * Posts a sealed Commit and registers it as a pending epoch operation.
     *
     * @param localStateApplied whether the new epoch is already adopted locally.
     *   Pass false when posting before adopting — the echo then becomes the
     *   instruction to apply it, which is how a client that died mid-post
     *   recovers.
     */
    suspend fun postCommit(
        gid: String,
        sealedBase64: String,
        localStateApplied: Boolean = true,
    ): PostedMessage {
        // Registered BEFORE the call returns, because the subscription can
        // deliver the echo while `postMessage` is still awaiting its own
        // response. Registering afterwards leaves a window in which our own
        // Commit looks like a stranger's and gets applied twice.
        inbox(gid).expectEcho(PendingEpochOperation(sealedBase64, localStateApplied))
        return client.postMessage(gid, sealedBase64)
    }

    /** Groups with a Commit posted but not yet seen coming back. */
    fun unconfirmed(): Map<String, List<PendingEpochOperation>> = inboxes.mapValues { it.value.pending() }.filterValues { it.isNotEmpty() }

    companion object {
        /** Enough for a long history, short of letting startup hang. */
        const val DEFAULT_MAX_PAGES = 64
    }
}

/** One message drained from a group, with what the inbox decided about it. */
data class DeliveredMessage(
    val gid: String,
    val message: GroupMessage,
    val ingestion: Ingestion,
)

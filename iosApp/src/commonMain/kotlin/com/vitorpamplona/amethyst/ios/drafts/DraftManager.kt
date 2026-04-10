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
package com.vitorpamplona.amethyst.ios.drafts

import com.vitorpamplona.amethyst.ios.network.IosRelayConnectionManager
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Display data for a draft in the UI.
 */
data class DraftDisplayData(
    val dTag: String,
    val content: String,
    val createdAt: Long,
    val replyToNoteId: String? = null,
)

/**
 * Manages NIP-37 draft events for the iOS app.
 *
 * Drafts are saved as encrypted kind 31234 events (DraftWrapEvent).
 * They are broadcast to the user's relays and can be resumed on any device.
 */
class DraftManager(
    private val signer: NostrSignerInternal,
    private val relayManager: IosRelayConnectionManager,
) {
    private val _drafts = MutableStateFlow<List<DraftDisplayData>>(emptyList())
    val drafts: StateFlow<List<DraftDisplayData>> = _drafts.asStateFlow()

    // In-memory cache of draft wrap events keyed by dTag
    private val draftEvents = mutableMapOf<String, DraftWrapEvent>()

    /**
     * Saves a draft note to relays. If a draft with the same dTag exists, it is updated.
     *
     * @param content The note text
     * @param dTag Unique identifier for this draft (use a stable ID per compose session)
     * @param replyToNoteId Optional note ID this draft is replying to
     */
    suspend fun saveDraft(
        content: String,
        dTag: String,
        replyToNoteId: String? = null,
    ) {
        try {
            if (content.isBlank()) return

            // Create an unsigned inner TextNoteEvent as the draft payload
            val innerTemplate = TextNoteEvent.build(content) {}
            val innerEvent = signer.sign(innerTemplate)

            // Wrap it in a DraftWrapEvent (encrypted, addressable)
            val draftWrap =
                DraftWrapEvent.create(
                    dTag = dTag,
                    draft = innerEvent,
                    signer = signer,
                )

            // Broadcast to relays
            relayManager.broadcastToAll(draftWrap as Event)

            // Update local cache
            draftEvents[dTag] = draftWrap
            updateDraftsList()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            platform.Foundation.NSLog("DraftManager.saveDraft error: " + (e.message ?: "unknown"))
        }
    }

    /**
     * Deletes a draft by broadcasting an empty (deleted) version.
     */
    suspend fun deleteDraft(dTag: String) {
        try {
            val deletedDraft =
                DraftWrapEvent.createDeletedEvent(
                    dTag = dTag,
                    signer = signer,
                )
            relayManager.broadcastToAll(deletedDraft as Event)

            draftEvents.remove(dTag)
            updateDraftsList()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            platform.Foundation.NSLog("DraftManager.deleteDraft error: " + (e.message ?: "unknown"))
        }
    }

    /**
     * Decrypts and returns the content of a draft.
     */
    suspend fun getDraftContent(dTag: String): String? {
        try {
            val wrap = draftEvents[dTag] ?: return null
            if (wrap.isDeleted()) return null
            val inner = wrap.decryptInnerEvent(signer)
            return inner.content
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            platform.Foundation.NSLog("DraftManager.getDraftContent error: " + (e.message ?: "unknown"))
            return null
        }
    }

    /**
     * Consumes a DraftWrapEvent received from a relay.
     */
    suspend fun consumeDraft(event: DraftWrapEvent) {
        try {
            if (event.pubKey != signer.pubKey) return

            val existing = draftEvents[event.dTag()]
            if (existing != null && existing.createdAt >= event.createdAt) return

            if (event.isDeleted()) {
                draftEvents.remove(event.dTag())
            } else {
                draftEvents[event.dTag()] = event
            }
            updateDraftsList()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            platform.Foundation.NSLog("DraftManager.consumeDraft error: " + (e.message ?: "unknown"))
        }
    }

    /**
     * Generates a new unique dTag for a compose session.
     */
    fun newDraftTag(): String = "ios-draft-${TimeUtils.now()}-${(0..9999).random()}"

    private suspend fun updateDraftsList() {
        val list =
            draftEvents.values
                .filter { !it.isDeleted() }
                .sortedByDescending { it.createdAt }
                .mapNotNull { wrap ->
                    try {
                        val inner = wrap.decryptInnerEvent(signer)
                        DraftDisplayData(
                            dTag = wrap.dTag(),
                            content = inner.content,
                            createdAt = wrap.createdAt,
                        )
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        null
                    }
                }
        _drafts.value = list
    }

    fun clear() {
        draftEvents.clear()
        _drafts.value = emptyList()
    }
}

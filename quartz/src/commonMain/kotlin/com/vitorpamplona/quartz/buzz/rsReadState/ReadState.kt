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
package com.vitorpamplona.quartz.buzz.rsReadState

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-RS (Buzz cross-device read state) helpers.
 *
 * KIND COLLISION — FLAGGED. NIP-RS reuses **`kind:30078`**, which in Quartz is already
 * [AppSpecificDataEvent] (NIP-78). This module deliberately does NOT introduce a competing
 * `kind:30078` [com.vitorpamplona.quartz.nip01Core.core.Event] subclass — a second class on the
 * same kind would break [com.vitorpamplona.quartz.utils.EventFactory] dispatch. Instead it is a
 * thin content/tag layer *on top of* [AppSpecificDataEvent]:
 *  - the `d` tag is `read-state:<slot-id>` ([dTagFor] / [slotIdFrom]),
 *  - a single `["t", "read-state"]` tag enables relay-side filtering,
 *  - `content` is a NIP-44 self-encrypted [ReadStateContent] (conversation key
 *    `nip44(user_privkey, user_pubkey)` — the user's own key on both sides).
 *
 * Ground truth: `docs/nips/NIP-RS.md` (there is no Rust — read state is client-side only).
 */
object ReadState {
    const val D_TAG_PREFIX = "read-state:"
    const val T_TAG_VALUE = "read-state"

    /** The addressable `d`-tag value for a given slot id. */
    fun dTagFor(slotId: String): String = "$D_TAG_PREFIX$slotId"

    /** Extracts the `<slot-id>` from a `read-state:<slot-id>` d-tag value, or `null` if it does not match. */
    fun slotIdFrom(dTagValue: String): String? = dTagValue.removePrefix(D_TAG_PREFIX).takeIf { it != dTagValue && it.isNotEmpty() }

    /** True if [tags] are a well-formed NIP-RS coordinate: exactly one `read-state:` d-tag and one `["t","read-state"]`. */
    fun isReadState(tags: TagArray): Boolean {
        val dTags = tags.count { it.size > 1 && it[0] == "d" }
        val readStateT = tags.count { it.size > 1 && it[0] == HashtagTag.TAG_NAME && it[1] == T_TAG_VALUE }
        val dValue = tags.firstOrNull { it.size > 1 && it[0] == "d" }?.getOrNull(1)
        return dTags == 1 && readStateT == 1 && dValue != null && dValue.startsWith(D_TAG_PREFIX)
    }

    /** Adds the NIP-RS `["t", "read-state"]` filter tag to an [AppSpecificDataEvent] builder. */
    fun TagArrayBuilder<AppSpecificDataEvent>.readStateHashtag() = addUnique(HashtagTag.assemble(T_TAG_VALUE))

    /**
     * Builds and signs a NIP-RS read-state event as an [AppSpecificDataEvent] (`kind:30078`):
     * [content] is NIP-44 self-encrypted and placed under the `read-state:[slotId]` coordinate.
     */
    suspend fun create(
        slotId: String,
        content: ReadStateContent,
        signer: NostrSigner,
        createdAt: Long = TimeUtils.now(),
    ): AppSpecificDataEvent {
        val ciphertext = signer.nip44Encrypt(content.encodeToJson(), signer.pubKey)
        return signer.sign(
            AppSpecificDataEvent.build(dTagFor(slotId), ciphertext, createdAt) {
                readStateHashtag()
            },
        )
    }

    /** Decrypts and parses the read-state blob from a NIP-RS [AppSpecificDataEvent]; [signer] must own the event. */
    suspend fun decrypt(
        event: AppSpecificDataEvent,
        signer: NostrSigner,
    ): ReadStateContent {
        val json = signer.decrypt(event.content, signer.pubKey)
        return ReadStateContent.decodeFromJson(json)
    }
}

/** The `<slot-id>` of a NIP-RS read-state event, or `null` if this is an ordinary NIP-78 app-data event. */
fun AppSpecificDataEvent.readStateSlotId(): String? = ReadState.slotIdFrom(dTag())

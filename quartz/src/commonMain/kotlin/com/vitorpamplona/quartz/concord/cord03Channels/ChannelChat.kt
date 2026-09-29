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
package com.vitorpamplona.quartz.concord.cord03Channels

import com.vitorpamplona.quartz.concord.cord03Channels.tags.ChannelTag
import com.vitorpamplona.quartz.concord.cord03Channels.tags.EpochTag
import com.vitorpamplona.quartz.concord.cord03Channels.tags.MsTag
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.concord.envelope.OpenedStreamEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip17Dm.files.tags.EncryptionAlgo
import com.vitorpamplona.quartz.nip17Dm.files.tags.EncryptionKey
import com.vitorpamplona.quartz.nip17Dm.files.tags.EncryptionNonce
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.IMetaTagBuilder
import com.vitorpamplona.quartz.nip94FileMetadata.tags.BlurhashTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.OriginalHashTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.ThumbhashTag
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.utils.ciphers.AESGCM

/**
 * Chat Plane message binding (CORD-03).
 *
 * A Concord chat rumor **is** a standard Nostr event — a kind-9 [ChatEvent]
 * message/reply or a kind-7 [ReactionEvent] — that additionally commits to the
 * channel and epoch it belongs to via `["channel", <id>]` + `["epoch", <n>]` tags
 * (see [channel]/[epoch] and [ChannelTag]/[EpochTag]). This object reuses the
 * standard event builders and only adds the binding, so the same event classes
 * that render everywhere else in the app render Concord messages too. Recipients
 * enforce the binding ([TagArray.isConcordBoundTo]) so an event lifted from one
 * channel/epoch can't be replayed into another.
 */
object ChannelChat {
    /**
     * Builds an unsigned kind-9 [ChatEvent] rumor bound to [channelId]/[epoch].
     * Wrap it for the channel plane with
     * [com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope] to publish.
     */
    fun message(
        authorPubKey: HexKey,
        channelId: HexKey,
        epoch: Long,
        text: String,
        createdAt: Long,
        extraTags: Array<Array<String>> = emptyArray(),
        ms: Int = MsTag.remainderFor(createdAt),
    ): Event =
        RumorAssembler.assembleRumor(
            authorPubKey,
            ChatEvent.build(text, createdAt) {
                channelBinding(channelId, epoch, ms)
                withoutBinding(extraTags).forEach { add(it) }
            },
        )

    /**
     * Builds an unsigned kind-9 **inline quote-reply** to [parentId]: a normal
     * channel [message] that quotes the parent via a `q` tag (NIP-C7) and credits
     * its author with a `p` tag. Unlike [reply] (a kind-1111 thread comment pulled
     * into a minichat), an inline quote stays in the main chat timeline — the two
     * reply modes the composer offers. Matches Armada, where a kind-9 `q` is an
     * inline quote deliberately kept out of threads.
     *
     * The `q` tag is the four-element NIP-C7 form `["q", <rumor id>, "", <author>]` the spec's
     * examples (§2.1) and Armada write: an empty relay hint (a rumor lives on no relay) and the
     * quoted author, so a reader can render the card before the quoted rumor arrives.
     */
    fun inlineReply(
        authorPubKey: HexKey,
        channelId: HexKey,
        epoch: Long,
        text: String,
        parentId: HexKey,
        parentAuthor: HexKey,
        createdAt: Long,
        extraTags: Array<Array<String>> = emptyArray(),
        ms: Int = MsTag.remainderFor(createdAt),
    ): Event =
        message(
            authorPubKey = authorPubKey,
            channelId = channelId,
            epoch = epoch,
            text = text,
            createdAt = createdAt,
            extraTags = arrayOf(arrayOf("q", parentId, "", parentAuthor), arrayOf("p", parentAuthor)) + extraTags,
            ms = ms,
        )

    /**
     * Builds an unsigned kind-3302 [ConcordChatEditEvent] rumor that edits an existing
     * channel message [targetId] with [newText], bound to [channelId]/[epoch].
     *
     * This is the dedicated Concord edit kind (CORD-02 Appendix B) the reference client
     * (Soapbox Armada's `KIND_EDIT`) emits — a separate rumor naming the target with a
     * single `["e", …]` tag, carrying the replacement text, wrapped and published on the
     * same channel plane as any other Chat Plane rumor. Armada omits a `k` tag on edits
     * (only deletes carry one), so we do too, for wire parity. On the receiving side it
     * decrypts to a kind-3302 that the fold overlays onto the target — latest edit wins,
     * and only edits authored by the *original* message's author are applied, so a member
     * can't rewrite someone else's message. It's non-destructive: the original keeps its
     * id, so reactions/replies/quotes stay attached.
     */
    fun edit(
        authorPubKey: HexKey,
        channelId: HexKey,
        epoch: Long,
        targetId: HexKey,
        newText: String,
        createdAt: Long,
        extraTags: Array<Array<String>> = emptyArray(),
        ms: Int = MsTag.remainderFor(createdAt),
    ): Event =
        RumorAssembler.assembleRumor<ConcordChatEditEvent>(
            pubKey = authorPubKey,
            createdAt = createdAt,
            kind = ConcordChatEditEvent.KIND,
            tags = bindingTags(channelId, epoch, ms) + arrayOf(arrayOf("e", targetId)) + withoutBinding(extraTags),
            content = newText,
        )

    /**
     * Builds an unsigned kind-5 **delete** rumor (CORD-01 Deletions, examples §2.4) retracting
     * the author's own [targets] inside the channel, bound to [channelId]/[epoch].
     *
     * NIP-09 shape: one `["e", <rumor id>]` per target, then one `["k", <kind>]` per distinct
     * target kind (`9` for a message, `1111` for a thread reply, `7` for a reaction), and the
     * optional [reason] as content. It names *rumor* ids relays never saw, so it must be wrapped
     * on the channel plane like any other Chat rumor — never published as a signed kind 5 or a
     * NIP-17 DM, both of which would leak the rumor ids outside the community. Receivers honor it
     * only for targets the delete's own author wrote. A delete never expires (CORD-08).
     */
    fun delete(
        authorPubKey: HexKey,
        channelId: HexKey,
        epoch: Long,
        targets: List<Event>,
        createdAt: Long,
        reason: String = "",
        ms: Int = MsTag.remainderFor(createdAt),
    ): Event {
        require(targets.isNotEmpty()) { "A delete must name at least one target" }
        val eTags = targets.map { arrayOf("e", it.id) }
        val kTags = targets.map { it.kind }.distinct().map { arrayOf("k", it.toString()) }
        return RumorAssembler.assembleRumor<DeletionRequestEvent>(
            pubKey = authorPubKey,
            createdAt = createdAt,
            kind = DeletionRequestEvent.KIND,
            tags = bindingTags(channelId, epoch, ms) + eTags.toTypedArray() + kTags.toTypedArray(),
            content = reason,
        )
    }

    /**
     * Builds an unsigned kind-1111 **thread reply** ([CommentEvent], NIP-22) to
     * [parent], bound to [channelId]/[epoch].
     *
     * A thread reply is a NIP-22 comment — NOT a kind-9 message with a `q` tag
     * (which NIP-C7 reserves for *inline quotes* that clients deliberately keep out
     * of threads). [CommentEvent.replyBuilder] emits the uppercase `K`/`E`/`P`
     * pointers at the immutable thread root and the lowercase `k`/`e`/`p` pointers
     * at the immediate [parent] (inheriting the root when [parent] is itself a
     * comment, so the root is stable at any depth). We add the same
     * `["channel", …]` + `["epoch", …]` binding every Chat Plane rumor carries, so
     * the reply is verifiable against the plane it arrives on. This is exactly the
     * shape Soapbox Armada builds and groups into a message's thread.
     */
    fun reply(
        authorPubKey: HexKey,
        channelId: HexKey,
        epoch: Long,
        text: String,
        parent: Event,
        createdAt: Long,
        extraTags: Array<Array<String>> = emptyArray(),
        ms: Int = MsTag.remainderFor(createdAt),
    ): Event =
        RumorAssembler.assembleRumor(
            authorPubKey,
            CommentEvent.replyBuilder(text, EventHintBundle(parent), createdAt) {
                channelBinding(channelId, epoch, ms)
                withoutBinding(extraTags).forEach { add(it) }
            },
        )

    /**
     * Builds an unsigned kind-1111 **thread reply carrying encrypted image** attachments ([imetas]) to
     * [parent], bound to [channelId]/[epoch]. Combines [reply]'s NIP-22 thread pointers with
     * [imageMessage]'s attachment handling: each ciphertext URL not already in [text] is appended to the
     * content (so the shared feed renders it) and each rides as a NIP-92 `imeta` tag
     * ([encryptedImageImeta]). This is the minichat counterpart of [imageMessage] — an image sent as a
     * thread reply instead of a top-level channel message.
     */
    fun imageReply(
        authorPubKey: HexKey,
        channelId: HexKey,
        epoch: Long,
        text: String,
        imetas: List<IMetaTag>,
        parent: Event,
        createdAt: Long,
        extraTags: Array<Array<String>> = emptyArray(),
        ms: Int = MsTag.remainderFor(createdAt),
    ): Event {
        val extraUrls = imetas.map { it.url }.filter { it.isNotBlank() && !text.contains(it) }
        val finalText = (listOf(text) + extraUrls).filter { it.isNotBlank() }.joinToString("\n")
        return reply(
            authorPubKey = authorPubKey,
            channelId = channelId,
            epoch = epoch,
            text = finalText,
            parent = parent,
            createdAt = createdAt,
            extraTags = imetas.map { it.toTagArray() }.toTypedArray() + extraTags,
            ms = ms,
        )
    }

    /**
     * Builds an unsigned kind-7 [ReactionEvent] rumor bound to [channelId]/[epoch]
     * against the target message ([targetId]/[targetAuthor]/[targetKind]). [content]
     * is the reaction (e.g. `"+"`, `"🤙"`). Kept to the minimal `e`/`p`/`k` tag form
     * (no relay hints) so it stays wire-identical across clients. On the receiving
     * side it decrypts to a normal kind-7 that wires to its target Note by the `e`
     * tag through the shared cache.
     */
    fun reaction(
        authorPubKey: HexKey,
        channelId: HexKey,
        epoch: Long,
        targetId: HexKey,
        targetAuthor: HexKey,
        targetKind: Int,
        content: String,
        createdAt: Long,
        extraTags: Array<Array<String>> = emptyArray(),
        ms: Int = MsTag.remainderFor(createdAt),
    ): Event =
        RumorAssembler.assembleRumor<ReactionEvent>(
            pubKey = authorPubKey,
            createdAt = createdAt,
            kind = ReactionEvent.KIND,
            tags =
                bindingTags(channelId, epoch, ms) +
                    arrayOf(
                        arrayOf("e", targetId),
                        arrayOf("p", targetAuthor),
                        arrayOf("k", targetKind.toString()),
                    ) + withoutBinding(extraTags),
            content = content,
        )

    /**
     * Builds an unsigned kind-9 message carrying one or more **encrypted image** attachments
     * ([imetas]), wire-identical to Soapbox Armada's `encryptAttachments` path so images interop
     * across Concord clients. Each attachment's ciphertext URL is appended to the text content (the
     * ones not already present), exactly as Armada assembles it, and each rides as a NIP-92 `imeta`
     * tag ([encryptedImageImeta]). The message is still a normal channel-bound kind-9, so the shared
     * feed renders it and the binding is enforced like any other Chat Plane rumor.
     */
    fun imageMessage(
        authorPubKey: HexKey,
        channelId: HexKey,
        epoch: Long,
        text: String,
        imetas: List<IMetaTag>,
        createdAt: Long,
        extraTags: Array<Array<String>> = emptyArray(),
        ms: Int = MsTag.remainderFor(createdAt),
    ): Event {
        val extraUrls = imetas.map { it.url }.filter { it.isNotBlank() && !text.contains(it) }
        val finalText = (listOf(text) + extraUrls).filter { it.isNotBlank() }.joinToString("\n")
        return message(
            authorPubKey = authorPubKey,
            channelId = channelId,
            epoch = epoch,
            text = finalText,
            createdAt = createdAt,
            extraTags = imetas.map { it.toTagArray() }.toTypedArray() + extraTags,
            ms = ms,
        )
    }

    /**
     * Builds the encrypted-image `imeta` tag Armada's `ChatComposer` emits with `encryptAttachments`:
     * `url` (ciphertext blob), `m` (plaintext mime), `dim`, `blurhash`, plus `encryption-algorithm`
     * (`aes-gcm`), `decryption-key`, `decryption-nonce` (hex), and `ox` (the *plaintext* SHA-256 for
     * integrity). Deliberately omits `x` (a ciphertext hash) to match Armada exactly. When a
     * [thumbhash] is available it is added as an extra (additive, ignored by clients that don't read
     * it) so receivers can paint a placeholder while the blob decrypts.
     */
    fun encryptedImageImeta(
        url: String,
        mimeType: String?,
        dim: String?,
        blurhash: String?,
        cipher: AESGCM,
        originalHash: String?,
        thumbhash: String? = null,
    ): IMetaTag =
        IMetaTagBuilder(url)
            .apply {
                mimeType?.let { add("m", it) }
                dim?.let { add("dim", it) }
                blurhash?.let { add(BlurhashTag.TAG_NAME, it) }
                thumbhash?.let { add(ThumbhashTag.TAG_NAME, it) }
                add(EncryptionAlgo.TAG_NAME, cipher.name())
                add(EncryptionKey.TAG_NAME, cipher.keyBytes.toHexKey())
                add(EncryptionNonce.TAG_NAME, cipher.nonce.toHexKey())
                originalHash?.let { add(OriginalHashTag.TAG_NAME, it) }
            }.build()

    /**
     * Parses every **encrypted image** attachment ([ConcordImageAttachment]) carried on [rumor] as an
     * `imeta` tag with the `aes-gcm` `decryption-key`/`decryption-nonce` fields. A plaintext imeta
     * (no encryption fields) is ignored here — it renders through the normal media path.
     */
    fun encryptedImagesOf(rumor: Event): List<ConcordImageAttachment> =
        rumor.tags
            .mapNotNull { if (it.size >= 2 && it[0] == IMetaTag.TAG_NAME) IMetaTag.parse(it) else null }
            .flatten()
            .mapNotNull { it.toEncryptedAttachmentOrNull() }

    private fun IMetaTag.prop(key: String): String? = properties[key]?.firstOrNull()?.takeIf { it.isNotEmpty() }

    private fun IMetaTag.toEncryptedAttachmentOrNull(): ConcordImageAttachment? {
        val key = prop(EncryptionKey.TAG_NAME) ?: return null
        val nonce = prop(EncryptionNonce.TAG_NAME) ?: return null
        val algo = prop(EncryptionAlgo.TAG_NAME) ?: return null
        val keyBytes = runCatching { key.hexToByteArray() }.getOrNull() ?: return null
        val nonceBytes = runCatching { nonce.hexToByteArray() }.getOrNull() ?: return null
        return ConcordImageAttachment(
            url = url,
            mimeType = prop("m"),
            dim = prop("dim"),
            blurhash = prop("blurhash"),
            algo = algo,
            key = keyBytes,
            nonce = nonceBytes,
            originalHash = prop(OriginalHashTag.TAG_NAME),
        )
    }

    /** Chat Plane typing indicator (CORD-03): a transient "user is composing" heartbeat. */
    const val KIND_TYPING = 23311

    /**
     * Builds an unsigned kind-23311 typing heartbeat bound to [channelId]/[epoch].
     * Empty content; wrap it as an **ephemeral** stream event (kind 21059) so relays
     * broadcast but never store it. Republish every few seconds while composing; a
     * receiver shows the author as typing until the heartbeat goes stale.
     */
    fun typing(
        authorPubKey: HexKey,
        channelId: HexKey,
        epoch: Long,
        createdAt: Long,
        ms: Int = MsTag.remainderFor(createdAt),
    ): Event =
        RumorAssembler.assembleRumor<Event>(
            pubKey = authorPubKey,
            createdAt = createdAt,
            kind = KIND_TYPING,
            tags = bindingTags(channelId, epoch, ms),
            content = "",
        )

    /** True when [rumor] is a typing heartbeat (kind 23311). */
    fun isTyping(rumor: Event): Boolean = rumor.kind == KIND_TYPING

    /** The channel id a Chat Plane [rumor] is bound to, or null if unbound. */
    fun channelOf(rumor: Event): HexKey? = rumor.tags.concordChannel()

    /** The epoch a Chat Plane [rumor] is bound to, or null if unbound/malformed. */
    fun epochOf(rumor: Event): Long? = rumor.tags.concordEpoch()

    /**
     * True when [rumor] is bound to exactly [channelId] and [epoch]. Recipients must
     * reject any Chat Plane event whose binding does not match the plane it arrived on.
     */
    fun isBoundTo(
        rumor: Event,
        channelId: HexKey,
        epoch: Long,
    ): Boolean = rumor.tags.isConcordBoundTo(channelId, epoch)

    /** Timer notice (CORD-08 §4), a Chat Plane kind. */
    const val KIND_TIMER_NOTICE = 1740

    /**
     * Every rumor kind a Chat Plane may carry into the app (CORD-02 Appendix B): messages,
     * thread replies, reactions, deletes, edits, the typing heartbeat and the CORD-08 timer
     * notice. Chat ingest refuses anything else — above all the other planes' kinds (a Control
     * edition 3308, a Guestbook 3306/3309/3312, a rekey 3303, a direct invite 3313): the planes
     * share one store, so without this a channel key-holder could inject a rumor another reader
     * would take for a Control edition (the reference client's `PLANE_KINDS` refusal).
     */
    val CHAT_KINDS: Set<Int> =
        setOf(
            ChatEvent.KIND,
            CommentEvent.KIND,
            ReactionEvent.KIND,
            DeletionRequestEvent.KIND,
            ConcordChatEditEvent.KIND,
            KIND_TYPING,
            KIND_TIMER_NOTICE,
        )

    /** True when [kind] may ride a Chat Plane ([CHAT_KINDS]). */
    fun isChatKind(kind: Int): Boolean = kind in CHAT_KINDS

    /**
     * The Chat Plane ingest gate for a wrap already opened under [channelId]'s key at [epoch]:
     * returns its rumor only when every Chat rule holds, else null (drop it).
     *  - the seal is the encrypted kind 20013 (CORD-02 §5: a plaintext 20014 seal is Control-only);
     *  - the rumor kind is a Chat kind ([CHAT_KINDS]), never another plane's;
     *  - the binding is strict: exactly one `channel` and one `epoch`, equal to the plane's
     *    ([isBoundTo], CORD-03 §3);
     *  - its `ms` tag, if any, is well formed (CORD-02 §4/§5 — a malformed one is dropped, never
     *    interpreted).
     */
    fun acceptOpened(
        opened: OpenedStreamEvent,
        channelId: HexKey,
        epoch: Long,
    ): Event? {
        if (opened.sealKind != ConcordStreamEnvelope.KIND_SEAL_ENCRYPTED) return null
        val rumor = opened.rumor
        if (!isChatKind(rumor.kind)) return null
        if (!isBoundTo(rumor, channelId, epoch)) return null
        if (orderingMs(rumor) == null) return null
        return rumor
    }

    /**
     * The rumor's CORD-02 §4 ordering time, `createdAt * 1000 + ms`, or null when its `ms` tag is
     * malformed or duplicated (such a rumor is dropped, never interpreted). See [MsTag].
     */
    fun orderingMs(rumor: Event): Long? = MsTag.orderingMs(rumor.createdAt, rumor.tags)

    /** The binding every Chat rumor commits, in the examples' order: channel, epoch, ms. */
    private fun bindingTags(
        channelId: HexKey,
        epoch: Long,
        ms: Int,
    ): Array<Array<String>> = arrayOf(ChannelTag.assemble(channelId), EpochTag.assemble(epoch), MsTag.assemble(ms))

    private val BINDING_TAG_NAMES = setOf(ChannelTag.TAG_NAME, EpochTag.TAG_NAME, MsTag.TAG_NAME)

    /**
     * [extraTags] minus any binding tag: a caller's extra `channel`/`epoch`/`ms` would make the
     * binding ambiguous, and strict receivers (ours included) drop a duplicated binding.
     */
    private fun withoutBinding(extraTags: Array<Array<String>>): Array<Array<String>> = extraTags.filterNot { it.isNotEmpty() && it[0] in BINDING_TAG_NAMES }.toTypedArray()
}

/**
 * A decrypted-pointer to an **encrypted image** attached to a Concord chat message (CORD-03), parsed
 * from a NIP-92 `imeta` tag ([ChannelChat.encryptedImagesOf]). The [url] blob is AES-256-GCM
 * ciphertext on a media host; fetch it, decrypt with [key]/[nonce], and verify the plaintext SHA-256
 * equals [originalHash] before displaying. Mirrors Soapbox Armada's encrypted attachment for interop.
 */
class ConcordImageAttachment(
    val url: String,
    val mimeType: String?,
    val dim: String?,
    val blurhash: String?,
    val algo: String,
    val key: ByteArray,
    val nonce: ByteArray,
    val originalHash: String?,
)

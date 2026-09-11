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
package com.vitorpamplona.quartz.marmot.mip05PushNotifications

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.sha256.sha256

/** The two record shapes an owner proof can cover. */
enum class PushRecordKind(
    val domainTag: String,
) {
    /** A token record (gossip kinds 447 / 448). */
    TOKEN("marmot-push-token-record-v1"),

    /** A token removal (gossip kind 449). */
    REMOVAL("marmot-push-token-removal-v1"),
}

/**
 * The push token owner proof — a BIP-340 signature over the id of an exact,
 * UNPUBLISHED kind `451` Nostr event (`features/push-notifications.md`,
 * "Owner authentication").
 *
 * ## Why an event that is never published
 *
 * The proof needs to bind a lot of context at once — which group, which
 * notification server, which relay hint, which token, and when — and a Nostr
 * event id is a ready-made canonical digest over exactly that kind of tuple.
 * Only the 64-byte signature travels, inside the gossip record; the event is a
 * signing template and MUST NOT be sent to a relay.
 *
 * ## What the binding buys
 *
 * Because the id covers `group_id`, `server_pubkey`, `relay_hint`, the
 * encrypted token and `owner_ts`, a member who merely RELAYS someone's record
 * cannot move it to another group, repoint it at a different notification
 * server or relay, swap the token, or restamp it. That matters because a
 * record's authority comes from `owner_sig` and current membership — never from
 * who happened to carry it.
 *
 * Note this is deliberately NOT the 104-byte [MarmotAuthorizationProof]
 * envelope: the account identity proof carries its own pubkey and timestamp,
 * while here both are already pinned by the record being signed over.
 */
object PushOwnerProof {
    const val KIND = 451

    /**
     * The superseded event-shaped proof kind.
     *
     * Accepted only when verifying in a LEGACY group, and never produced. Kind
     * `450` is the account identity proof's kind; push borrowed it before `451`
     * was allocated, and accepting it here does not reserve it for push.
     */
    const val LEGACY_KIND = 450

    /**
     * Build the tag list, in the exact order the spec fixes.
     *
     * Order and arity are not cosmetic — they are inside the id preimage, so a
     * reordered or duplicated tag yields a different id and the signature
     * simply does not verify. That is the intended failure mode.
     */
    fun tags(
        record: PushRecordKind,
        groupIdHex: HexKey,
        memberIdHex: HexKey,
        leafIndex: Int,
        platform: String,
        serverPubKeyHex: HexKey,
        tokenFingerprint: String,
        ownerTsMillis: Long,
        relayHint: String,
    ): TagArray {
        val base =
            mutableListOf(
                arrayOf("d", record.domainTag),
                arrayOf("group_id", groupIdHex),
                arrayOf("member_id", memberIdHex),
                arrayOf("leaf_index", leafIndex.toString()),
                arrayOf("platform", platform),
                arrayOf("server_pubkey", serverPubKeyHex),
                arrayOf("token_fingerprint", tokenFingerprint),
                arrayOf("owner_ts", ownerTsMillis.toString()),
                // A removal always encodes an empty hint; a token record carries
                // the member's exact string, with no trimming or normalization —
                // altering it would change the id the owner signed.
                arrayOf("relay_hint", if (record == PushRecordKind.REMOVAL) "" else relayHint),
            )
        if (record == PushRecordKind.TOKEN) {
            base.add(arrayOf("encrypted_token_encoding", "base64"))
        }
        return base.toTypedArray()
    }

    /** `created_at` is fixed at 0: the record's own `owner_ts` is the timestamp that counts. */
    const val CREATED_AT = 0L

    /** The id the owner signs. */
    fun eventId(
        memberIdHex: HexKey,
        tags: TagArray,
        content: String,
    ): ByteArray = EventHasher.hashIdBytes(memberIdHex, CREATED_AT, KIND, tags, content)

    /** The unsigned event an external signer is asked to sign. Never published. */
    fun signingTemplate(
        tags: TagArray,
        content: String,
    ): EventTemplate<Event> =
        EventTemplate(
            createdAt = CREATED_AT,
            kind = KIND,
            tags = tags,
            content = content,
        )

    /**
     * Ask [signer] for the owner proof over a record.
     *
     * The returned event is validated field by field before its signature is
     * copied out. An external signer — a bunker, a hardware device — is free to
     * return something other than what it was asked to sign, and a substituted
     * group id or server pubkey would otherwise become a proof that silently
     * authorizes the wrong destination.
     *
     * @return the 64-byte `owner_sig`.
     */
    suspend fun create(
        signer: NostrSigner,
        record: PushRecordKind,
        groupIdHex: HexKey,
        leafIndex: Int,
        platform: String,
        serverPubKeyHex: HexKey,
        tokenFingerprint: String,
        ownerTsMillis: Long,
        relayHint: String = "",
        encryptedTokenBase64: String = "",
    ): ByteArray {
        val memberIdHex = signer.pubKey
        val builtTags =
            tags(
                record,
                groupIdHex,
                memberIdHex,
                leafIndex,
                platform,
                serverPubKeyHex,
                tokenFingerprint,
                ownerTsMillis,
                relayHint,
            )
        val content = if (record == PushRecordKind.REMOVAL) "" else encryptedTokenBase64
        val signed: Event = signer.sign(signingTemplate(builtTags, content))

        require(signed.pubKey == memberIdHex) {
            "signer returned a push owner proof authored by a different account"
        }
        require(signed.createdAt == CREATED_AT && signed.kind == KIND && signed.content == content) {
            "signer returned a different push owner proof event than requested"
        }
        require(tagsEqual(signed.tags, builtTags)) {
            "signer altered the push owner proof tags"
        }
        val signature = signed.sig.hexToByteArray()
        require(verify(signature, memberIdHex, builtTags, content, KIND)) {
            "signer returned a push owner proof whose signature does not verify"
        }
        return signature
    }

    /**
     * Verify an `owner_sig` under [memberIdHex].
     *
     * [kind] selects the proof form. A CURRENT-profile group accepts only
     * [KIND]; a legacy group also accepts [LEGACY_KIND], so an upgraded member
     * and a not-yet-upgraded one can stay in the same group. Producers create
     * only [KIND].
     */
    fun verify(
        ownerSig: ByteArray,
        memberIdHex: HexKey,
        tags: TagArray,
        content: String,
        kind: Int = KIND,
    ): Boolean {
        if (ownerSig.size != 64) return false
        return try {
            val id = EventHasher.hashIdBytes(memberIdHex, CREATED_AT, kind, tags, content)
            Nip01Crypto.verify(ownerSig, id, memberIdHex.hexToByteArray())
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Verify a record's proof the way a recipient must.
     *
     * [currentProfileGroup] decides which forms are acceptable, and the
     * distinction is a security one rather than a courtesy: in a group where
     * every leaf carries a `0x8009` identity proof, accepting a weaker legacy
     * form would let anyone who can produce one bypass the stronger binding the
     * group already guarantees.
     *
     * A legacy group accepts two more forms, both verification-only and never
     * produced: the transitional kind [LEGACY_KIND] event deployed before `451`
     * was allocated, and the raw proof — a signature directly over the 32-byte
     * `SHA-256(SignedRecord)` digest. [signedRecord] supplies those canonical
     * bytes lazily, so a current-profile group never computes them at all.
     */
    fun verifyRecord(
        ownerSig: ByteArray,
        record: PushRecordKind,
        groupIdHex: HexKey,
        memberIdHex: HexKey,
        leafIndex: Int,
        platform: String,
        serverPubKeyHex: HexKey,
        tokenFingerprint: String,
        ownerTsMillis: Long,
        relayHint: String = "",
        encryptedTokenBase64: String = "",
        currentProfileGroup: Boolean,
        signedRecord: (() -> ByteArray)? = null,
    ): Boolean {
        val builtTags =
            tags(
                record,
                groupIdHex,
                memberIdHex,
                leafIndex,
                platform,
                serverPubKeyHex,
                tokenFingerprint,
                ownerTsMillis,
                relayHint,
            )
        val content = if (record == PushRecordKind.REMOVAL) "" else encryptedTokenBase64
        if (verify(ownerSig, memberIdHex, builtTags, content, KIND)) return true
        if (currentProfileGroup) return false
        if (verify(ownerSig, memberIdHex, builtTags, content, LEGACY_KIND)) return true
        val canonical = signedRecord?.invoke() ?: return false
        return verifyRawDigest(ownerSig, memberIdHex, canonical)
    }

    /**
     * The oldest accepted form: a BIP-340 signature straight over
     * `SHA-256(SignedRecord)`, with no event around it.
     *
     * Verification-only, and only in a legacy group. A producer MUST NOT create
     * it — it binds the same fields, but through a digest an external signer
     * cannot be asked to sign without handing it raw bytes, which is why the
     * current form is an event id instead.
     */
    fun verifyRawDigest(
        ownerSig: ByteArray,
        memberIdHex: HexKey,
        signedRecord: ByteArray,
    ): Boolean {
        if (ownerSig.size != 64) return false
        return try {
            Nip01Crypto.verify(ownerSig, sha256(signedRecord), memberIdHex.hexToByteArray())
        } catch (_: Exception) {
            false
        }
    }

    /** Hex form, for embedding in a gossip record. */
    fun toHex(ownerSig: ByteArray): HexKey = ownerSig.toHexKey()

    private fun tagsEqual(
        a: TagArray,
        b: TagArray,
    ): Boolean {
        if (a.size != b.size) return false
        for (i in a.indices) {
            if (!a[i].contentEquals(b[i])) return false
        }
        return true
    }
}

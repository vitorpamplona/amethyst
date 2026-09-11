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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey

/**
 * One push token record as it travels inside a kind `447` or `448` app event
 * (`features/push-notifications.md`, "Token entries").
 *
 * The entry is self-authenticating: [ownerSig] is the owning member's proof
 * over every other field, so a member that merely RELAYS the record in a kind
 * `448` cannot move it to another group, repoint it at a different notification
 * server or relay, swap the token, or restamp it. That is what lets a group
 * converge on the full token set without every owner being online.
 *
 * Fields here are already validated shapes — [PushGossip.decodeTokens] drops a
 * malformed entry rather than constructing one — but NOT yet verified. Whether
 * the signature is good, whether the member is current, and whether the stamp
 * wins its record key are all recipient decisions that need context this type
 * does not have.
 */
data class PushTokenEntry(
    val memberIdHex: HexKey,
    val leafIndex: Int,
    val platform: PushPlatform,
    val tokenFingerprint: String,
    val serverPubKeyHex: HexKey,
    /** Already trimmed; empty means absent. */
    val relayHint: String,
    /** Exactly 1084 bytes. */
    val encryptedToken: ByteArray,
    val ownerTsMillis: Long,
    /** Exactly 64 bytes. */
    val ownerSig: ByteArray,
) {
    val key get() = PushRecordKey(memberIdHex, leafIndex, platform, serverPubKeyHex)

    val encryptedTokenBase64: String get() = PushBase64.encode(encryptedToken)

    fun signedRecord(groupIdHex: HexKey): ByteArray =
        PushSignedRecord.encode(
            record = PushRecordKind.TOKEN,
            groupIdHex = groupIdHex,
            memberIdHex = memberIdHex,
            leafIndex = leafIndex,
            platform = platform,
            serverPubKeyHex = serverPubKeyHex,
            tokenFingerprint = tokenFingerprint,
            ownerTsMillis = ownerTsMillis,
            relayHint = relayHint,
            encryptedToken = encryptedToken,
        )

    fun stamp(groupIdHex: HexKey) = PushRecordStamp(ownerTsMillis, PushSignedRecord.digestHex(signedRecord(groupIdHex)))

    /**
     * Verify [ownerSig] the way a recipient must.
     *
     * [currentProfileGroup] is not a courtesy flag: in a group where every leaf
     * already carries a `0x8009` identity proof, accepting the weaker legacy
     * proof forms would throw away a binding the group otherwise guarantees.
     */
    fun verifyOwner(
        groupIdHex: HexKey,
        currentProfileGroup: Boolean,
    ): Boolean =
        PushOwnerProof.verifyRecord(
            ownerSig = ownerSig,
            record = PushRecordKind.TOKEN,
            groupIdHex = groupIdHex,
            memberIdHex = memberIdHex,
            leafIndex = leafIndex,
            platform = platform.wireName,
            serverPubKeyHex = serverPubKeyHex,
            tokenFingerprint = tokenFingerprint,
            ownerTsMillis = ownerTsMillis,
            relayHint = relayHint,
            encryptedTokenBase64 = encryptedTokenBase64,
            currentProfileGroup = currentProfileGroup,
            signedRecord = { signedRecord(groupIdHex) },
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PushTokenEntry) return false
        return memberIdHex == other.memberIdHex &&
            leafIndex == other.leafIndex &&
            platform == other.platform &&
            tokenFingerprint == other.tokenFingerprint &&
            serverPubKeyHex == other.serverPubKeyHex &&
            relayHint == other.relayHint &&
            encryptedToken.contentEquals(other.encryptedToken) &&
            ownerTsMillis == other.ownerTsMillis &&
            ownerSig.contentEquals(other.ownerSig)
    }

    override fun hashCode(): Int {
        var result = memberIdHex.hashCode()
        result = 31 * result + leafIndex
        result = 31 * result + platform.hashCode()
        result = 31 * result + tokenFingerprint.hashCode()
        result = 31 * result + serverPubKeyHex.hashCode()
        result = 31 * result + relayHint.hashCode()
        result = 31 * result + encryptedToken.contentHashCode()
        result = 31 * result + ownerTsMillis.hashCode()
        result = 31 * result + ownerSig.contentHashCode()
        return result
    }
}

/**
 * One revocation as it travels inside a kind `449` app event
 * (`features/push-notifications.md`, "Removal").
 *
 * It carries [leafIndex] for the same reason the record key does: without it a
 * removal would revoke every sibling device's token for the same account,
 * platform and server rather than the one device that asked to be forgotten.
 *
 * [tokenFingerprint] is signed over and states which token instance the owner
 * meant to revoke, but it does NOT gate the delete — the stamp alone decides
 * which write to a record key wins. A fingerprint-scoped tombstone could not
 * suppress a differently-fingerprinted stale record from resurrecting the key,
 * which is the whole job of a tombstone.
 */
data class PushRemovalEntry(
    val memberIdHex: HexKey,
    val leafIndex: Int,
    val platform: PushPlatform,
    val tokenFingerprint: String,
    val serverPubKeyHex: HexKey,
    val ownerTsMillis: Long,
    val ownerSig: ByteArray,
) {
    val key get() = PushRecordKey(memberIdHex, leafIndex, platform, serverPubKeyHex)

    fun signedRecord(groupIdHex: HexKey): ByteArray =
        PushSignedRecord.encode(
            record = PushRecordKind.REMOVAL,
            groupIdHex = groupIdHex,
            memberIdHex = memberIdHex,
            leafIndex = leafIndex,
            platform = platform,
            serverPubKeyHex = serverPubKeyHex,
            tokenFingerprint = tokenFingerprint,
            ownerTsMillis = ownerTsMillis,
        )

    fun stamp(groupIdHex: HexKey) = PushRecordStamp(ownerTsMillis, PushSignedRecord.digestHex(signedRecord(groupIdHex)))

    fun verifyOwner(
        groupIdHex: HexKey,
        currentProfileGroup: Boolean,
    ): Boolean =
        PushOwnerProof.verifyRecord(
            ownerSig = ownerSig,
            record = PushRecordKind.REMOVAL,
            groupIdHex = groupIdHex,
            memberIdHex = memberIdHex,
            leafIndex = leafIndex,
            platform = platform.wireName,
            serverPubKeyHex = serverPubKeyHex,
            tokenFingerprint = tokenFingerprint,
            ownerTsMillis = ownerTsMillis,
            currentProfileGroup = currentProfileGroup,
            signedRecord = { signedRecord(groupIdHex) },
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PushRemovalEntry) return false
        return memberIdHex == other.memberIdHex &&
            leafIndex == other.leafIndex &&
            platform == other.platform &&
            tokenFingerprint == other.tokenFingerprint &&
            serverPubKeyHex == other.serverPubKeyHex &&
            ownerTsMillis == other.ownerTsMillis &&
            ownerSig.contentEquals(other.ownerSig)
    }

    override fun hashCode(): Int {
        var result = memberIdHex.hashCode()
        result = 31 * result + leafIndex
        result = 31 * result + platform.hashCode()
        result = 31 * result + tokenFingerprint.hashCode()
        result = 31 * result + serverPubKeyHex.hashCode()
        result = 31 * result + ownerTsMillis.hashCode()
        result = 31 * result + ownerSig.contentHashCode()
        return result
    }
}

/** Hex helpers that hold the spec to LOWERCASE, which [com.vitorpamplona.quartz.utils.Hex] deliberately does not. */
internal object PushHex {
    fun isLower(
        value: String,
        length: Int,
    ): Boolean = value.length == length && value.all { it in '0'..'9' || it in 'a'..'f' }

    fun bytes(value: String): ByteArray = value.hexToByteArray()

    fun of(value: ByteArray): HexKey = value.toHexKey()
}

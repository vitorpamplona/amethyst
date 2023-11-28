package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner

const val POLL_OPTION = "poll_option"
const val VALUE_MAXIMUM = "value_maximum"
const val VALUE_MINIMUM = "value_minimum"
const val CONSENSUS_THRESHOLD = "consensus_threshold"
const val CLOSED_AT = "closed_at"

@Immutable
class PollNoteEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    // ots: String?, TODO implement OTS: https://github.com/opentimestamps/java-opentimestamps
    content: String,
    sig: HexKey
) : BaseTextNoteEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    fun pollOptions() =
        tags.filter { it.size > 2 && it[0] == POLL_OPTION }
            .associate { it[1].toInt() to it[2] }

    fun minimumAmount() = tags.firstOrNull() { it.size > 1 && it[0] == VALUE_MINIMUM }?.getOrNull(1)?.toLongOrNull()
    fun maximumAmount() = tags.firstOrNull() { it.size > 1 && it[0] == VALUE_MAXIMUM }?.getOrNull(1)?.toLongOrNull()

    fun getTagLong(property: String): Long? {
        val number = tags.firstOrNull() { it.size > 1 && it[0] == property }?.get(1)

        return if (number.isNullOrBlank() || number == "null") {
            null
        } else {
            number.toLong()
        }
    }

    companion object {
        const val kind = 6969

        fun create(
            msg: String,
            replyTos: List<String>?,
            mentions: List<String>?,
            addresses: List<ATag>?,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            pollOptions: Map<Int, String>,
            valueMaximum: Int?,
            valueMinimum: Int?,
            consensusThreshold: Int?,
            closedAt: Int?,
            zapReceiver: List<ZapSplitSetup>? = null,
            markAsSensitive: Boolean,
            zapRaiserAmount: Long?,
            geohash: String? = null,
            onReady: (PollNoteEvent) -> Unit
        ) {
            val tags = mutableListOf<Array<String>>()
            replyTos?.forEach {
                tags.add(arrayOf("e", it))
            }
            mentions?.forEach {
                tags.add(arrayOf("p", it))
            }
            addresses?.forEach {
                tags.add(arrayOf("a", it.toTag()))
            }
            pollOptions.forEach { poll_op ->
                tags.add(arrayOf(POLL_OPTION, poll_op.key.toString(), poll_op.value))
            }
            valueMaximum?.let {
                tags.add(arrayOf(VALUE_MAXIMUM, valueMaximum.toString()))
            }
            valueMinimum?.let {
                tags.add(arrayOf(VALUE_MINIMUM, valueMinimum.toString()))
            }
            consensusThreshold?.let {
                tags.add(arrayOf(CONSENSUS_THRESHOLD, consensusThreshold.toString()))
            }
            closedAt?.let {
                tags.add(arrayOf(CLOSED_AT, closedAt.toString()))
            }
            zapReceiver?.forEach {
                tags.add(arrayOf("zap", it.lnAddressOrPubKeyHex, it.relay ?: "", it.weight.toString()))
            }
            if (markAsSensitive) {
                tags.add(arrayOf("content-warning", ""))
            }
            zapRaiserAmount?.let {
                tags.add(arrayOf("zapraiser", "$it"))
            }
            geohash?.let {
                tags.addAll(geohashMipMap(it))
            }

            signer.sign(createdAt, kind, tags.toTypedArray(), msg, onReady)
        }
    }
}

/*
{
  "id": <32-bytes lowercase hex-encoded sha256 of the serialized event data>
  "pubkey": <32-bytes lowercase hex-encoded public key of the event creator>,
  "created_at": <unix timestamp in seconds>,
  "kind": 6969,
  "tags": [
    ["e", <32-bytes hex of the id of the poll event>, <primary poll host relay URL>],
    ["p", <32-bytes hex of the key>, <primary poll host relay URL>],
    ["poll_option", "0", "poll option 0 description string"],
    ["poll_option", "1", "poll option 1 description string"],
    ["poll_option", "n", "poll option <n> description string"],
    ["value_maximum", "maximum satoshi value for inclusion in tally"],
    ["value_minimum", "minimum satoshi value for inclusion in tally"],
    ["consensus_threshold", "required percentage to attain consensus <0..100>"],
    ["closed_at", "unix timestamp in seconds"],
  ],
  "ots": <base64-encoded OTS file data>
  "content": <primary poll description string>,
  "sig": <64-bytes hex of the signature of the sha256 hash of the serialized event data, which is the same as the "id" field>
}
 */

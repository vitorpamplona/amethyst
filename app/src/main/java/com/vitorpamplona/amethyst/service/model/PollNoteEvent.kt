package com.vitorpamplona.amethyst.service.model

import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import nostr.postr.Utils
import java.util.Date

const val POLL_OPTION = "poll_option"
const val VALUE_MAXIMUM = "value_maximum"
const val VALUE_MINIMUM = "value_minimum"
const val CONSENSUS_THRESHOLD = "consensus_threshold"
const val CLOSED_AT = "closed_at"

class PollNoteEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    // ots: String?, TODO implement OTS: https://github.com/opentimestamps/java-opentimestamps
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {
    fun mentions() = tags.filter { it.firstOrNull() == "p" }.mapNotNull { it.getOrNull(1) }
    fun taggedAddresses() = tags.filter { it.firstOrNull() == "a" }.mapNotNull {
        val aTagValue = it.getOrNull(1)
        val relay = it.getOrNull(2)

        if (aTagValue != null) ATag.parse(aTagValue, relay) else null
    }

    fun replyTos() = tags.filter { it.firstOrNull() == "e" }.mapNotNull { it.getOrNull(1) }

    fun pollOptions(): Map<Int, String> {
        val map = mutableMapOf<Int, String>()
        tags.filter { it.first() == POLL_OPTION }
            .forEach { map[it[1].toInt()] = it[2] }
        return map
    }
    fun valueMaximum(): Int? = tags.filter { it.firstOrNull() == VALUE_MAXIMUM }
        .getOrNull(1)?.getOrNull(1)?.toInt()

    fun valueMinimum(): Int? = tags.filter { it.firstOrNull() == VALUE_MINIMUM }
        .getOrNull(1)?.getOrNull(1)?.toInt()

    fun consensusThreshold(): Int? = tags.filter { it.firstOrNull() == CONSENSUS_THRESHOLD }
        .getOrNull(1)?.getOrNull(1)?.toInt()

    fun closedAt(): Int? = tags.filter { it.firstOrNull() == CLOSED_AT }
        .getOrNull(1)?.getOrNull(1)?.toInt()

    companion object {
        const val kind = 6969

        fun create(
            msg: String,
            replyTos: List<String>?,
            mentions: List<String>?,
            addresses: List<ATag>?,
            privateKey: ByteArray,
            createdAt: Long = Date().time / 1000,
            pollOptions: Map<Int, String>,
            valueMaximum: Int?,
            valueMinimum: Int?,
            consensusThreshold: Int?,
            closedAt: Int?
        ): PollNoteEvent {
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val tags = mutableListOf<List<String>>()
            replyTos?.forEach {
                tags.add(listOf("e", it))
            }
            mentions?.forEach {
                tags.add(listOf("p", it))
            }
            addresses?.forEach {
                tags.add(listOf("a", it.toTag()))
            }
            pollOptions.forEach { poll_op ->
                tags.add(listOf(POLL_OPTION, poll_op.key.toString(), poll_op.value))
            }
            tags.add(listOf(VALUE_MAXIMUM, valueMaximum.toString()))
            tags.add(listOf(VALUE_MINIMUM, valueMinimum.toString()))
            tags.add(listOf(CONSENSUS_THRESHOLD, consensusThreshold.toString()))
            tags.add(listOf(CLOSED_AT, closedAt.toString()))
            val id = generateId(pubKey, createdAt, kind, tags, msg)
            val sig = Utils.sign(id, privateKey)
            return PollNoteEvent(id.toHexKey(), pubKey, createdAt, tags, msg, sig.toHexKey())
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

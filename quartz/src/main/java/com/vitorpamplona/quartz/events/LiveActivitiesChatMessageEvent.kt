package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner

@Immutable
class LiveActivitiesChatMessageEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey
) : BaseTextNoteEvent(id, pubKey, createdAt, kind, tags, content, sig) {

    private fun innerActivity() = tags.firstOrNull {
        it.size > 3 && it[0] == "a" && it[3] == "root"
    } ?: tags.firstOrNull {
        it.size > 1 && it[0] == "a"
    }

    private fun activityHex() = innerActivity()?.let {
        it.getOrNull(1)
    }

    fun activity() = innerActivity()?.let {
        if (it.size > 1) {
            val aTagValue = it[1]
            val relay = it.getOrNull(2)

            ATag.parse(aTagValue, relay)
        } else {
            null
        }
    }

    override fun replyTos() = taggedEvents().minus(activityHex() ?: "")

    companion object {
        const val kind = 1311

        fun create(
            message: String,
            activity: ATag,
            replyTos: List<String>? = null,
            mentions: List<String>? = null,
            zapReceiver: List<ZapSplitSetup>? = null,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            markAsSensitive: Boolean,
            zapRaiserAmount: Long?,
            geohash: String? = null,
            onReady: (LiveActivitiesChatMessageEvent) -> Unit
        ) {
            val content = message
            val tags = mutableListOf(
                arrayOf("a", activity.toTag(), "", "root")
            )
            replyTos?.forEach {
                tags.add(arrayOf("e", it))
            }
            mentions?.forEach {
                tags.add(arrayOf("p", it))
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

            signer.sign(createdAt, kind, tags.toTypedArray(), content, onReady)
        }
    }
}

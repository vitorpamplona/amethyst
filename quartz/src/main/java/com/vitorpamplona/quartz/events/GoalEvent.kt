package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner

@Immutable
class GoalEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    companion object {
        const val kind = 9041
        const val alt = "Zap Goal"

        private const val SUMMARY = "summary"
        private const val CLOSED_AT = "closed_at"
        private const val IMAGE = "image"
        private const val AMOUNT = "amount"

        fun create(
            description: String,
            amount: Long,
            relays: Set<String>,
            closedAt: Long? = null,
            image: String? = null,
            summary: String? = null,
            websiteUrl: String? = null,
            linkedEvent: Event? = null,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (GoalEvent) -> Unit
        ) {
            var tags = mutableListOf(
                arrayOf(AMOUNT, amount.toString()),
                arrayOf("relays") + relays,
                arrayOf("alt", alt)
            )

            if (linkedEvent is AddressableEvent) {
                tags.add(arrayOf("a", linkedEvent.address().toTag()))
            } else if (linkedEvent is Event) {
                tags.add(arrayOf("e", linkedEvent.id))
            }

            closedAt?.let { tags.add(arrayOf(CLOSED_AT, it.toString())) }
            summary?.let { tags.add(arrayOf(SUMMARY, it)) }
            image?.let { tags.add(arrayOf(IMAGE, it)) }
            websiteUrl?.let { tags.add(arrayOf("r", it)) }

            signer.sign(createdAt, kind, tags.toTypedArray(), description, onReady)
        }
    }
}

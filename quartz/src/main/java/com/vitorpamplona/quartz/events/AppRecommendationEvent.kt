package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class AppRecommendationEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    fun recommendations() = tags.filter { it.size > 1 && it[0] == "a" }.mapNotNull {
        ATag.parse(it[1], it.getOrNull(2))
    }

    companion object {
        const val kind = 31989

        fun create(
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (AppRecommendationEvent) -> Unit
        ) {
            val tags = emptyArray<Array<String>>()
            signer.sign(createdAt, kind, tags, "", onReady)
        }
    }
}

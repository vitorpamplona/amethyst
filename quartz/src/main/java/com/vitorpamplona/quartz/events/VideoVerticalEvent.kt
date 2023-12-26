package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner

@Immutable
class VideoVerticalEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey
) : VideoEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    companion object {
        const val kind = 34236
        const val altDescription = "Vertical Video"

        fun create(
            url: String,
            magnetUri: String? = null,
            mimeType: String? = null,
            alt: String? = null,
            hash: String? = null,
            size: String? = null,
            dimensions: String? = null,
            blurhash: String? = null,
            originalHash: String? = null,
            magnetURI: String? = null,
            torrentInfoHash: String? = null,
            encryptionKey: AESGCM? = null,
            sensitiveContent: Boolean? = null,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (FileHeaderEvent) -> Unit
        ) {
            create(
                kind,
                url,
                magnetUri,
                mimeType,
                alt,
                hash,
                size,
                dimensions,
                blurhash,
                originalHash,
                magnetURI,
                torrentInfoHash,
                encryptionKey,
                sensitiveContent,
                altDescription,
                signer,
                createdAt,
                onReady
            )
        }
    }
}

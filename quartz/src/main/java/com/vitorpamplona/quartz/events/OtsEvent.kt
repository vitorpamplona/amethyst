/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.ots.BlockstreamExplorer
import com.vitorpamplona.quartz.ots.CalendarPureJavaBuilder
import com.vitorpamplona.quartz.ots.DetachedTimestampFile
import com.vitorpamplona.quartz.ots.Hash
import com.vitorpamplona.quartz.ots.OpenTimestamps
import com.vitorpamplona.quartz.ots.VerifyResult
import com.vitorpamplona.quartz.ots.op.OpSHA256
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import java.util.Base64

@Immutable
class OtsEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    @Transient
    var verifiedTime: Long? = null

    override fun isContentEncoded() = true

    fun digestEvent() = tags.firstOrNull { it.size > 1 && it[0] == "e" }?.get(1)

    fun digest() = digestEvent()?.hexToByteArray()

    fun otsByteArray(): ByteArray {
        return Base64.getDecoder().decode(content)
    }

    fun cacheVerify(): Long? {
        return if (verifiedTime != null) {
            verifiedTime
        } else {
            verifiedTime = verify()
            verifiedTime
        }
    }

    fun verify(): Long? {
        return digestEvent()?.let { OtsEvent.verify(otsByteArray(), it) }
    }

    fun info(): String {
        val detachedOts = DetachedTimestampFile.deserialize(otsByteArray())
        return otsInstance.info(detachedOts)
    }

    companion object {
        const val KIND = 1040
        const val ALT = "Opentimestamps Attestation"

        var otsInstance = OpenTimestamps(BlockstreamExplorer(), CalendarPureJavaBuilder())

        fun stamp(eventId: HexKey): String {
            val hash = Hash(eventId.hexToByteArray(), OpSHA256._TAG)
            val file = DetachedTimestampFile.from(hash)
            val timestamp = otsInstance.stamp(file)
            val detachedToSerialize = DetachedTimestampFile(hash.getOp(), timestamp)
            return Base64.getEncoder().encodeToString(detachedToSerialize.serialize())
        }

        fun upgrade(
            otsFile: String,
            eventId: HexKey,
        ): String {
            val detachedOts = DetachedTimestampFile.deserialize(Base64.getDecoder().decode(otsFile))

            return if (otsInstance.upgrade(detachedOts)) {
                // if the change is now verifiable.
                if (verify(detachedOts, eventId) != null) {
                    Base64.getEncoder().encodeToString(detachedOts.serialize())
                } else {
                    otsFile
                }
            } else {
                otsFile
            }
        }

        fun verify(
            otsFile: String,
            eventId: HexKey,
        ): Long? {
            return verify(Base64.getDecoder().decode(otsFile), eventId)
        }

        fun verify(
            otsFile: ByteArray,
            eventId: HexKey,
        ): Long? {
            return verify(DetachedTimestampFile.deserialize(otsFile), eventId)
        }

        fun verify(
            detachedOts: DetachedTimestampFile,
            eventId: HexKey,
        ): Long? {
            try {
                val result = otsInstance.verify(detachedOts, eventId.hexToByteArray())
                if (result == null || result.isEmpty()) {
                    return null
                } else {
                    return result.get(VerifyResult.Chains.BITCOIN)?.timestamp
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("OpenTimeStamps", "Failed to verify", e)
                return null
            }
        }

        fun create(
            eventId: HexKey,
            otsFileBase64: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (OtsEvent) -> Unit,
        ) {
            val tags =
                arrayOf(
                    arrayOf("e", eventId),
                    arrayOf("alt", ALT),
                )
            signer.sign(createdAt, KIND, tags, otsFileBase64, onReady)
        }
    }
}

/**
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
package com.vitorpamplona.quartz.nip03Timestamp.ots.attestation

import android.util.Log
import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamDeserializationContext
import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamSerializationContext
import com.vitorpamplona.quartz.nip03Timestamp.ots.Utils.compare
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.DeserializationException
import java.nio.charset.StandardCharsets

/**
 * Pending attestations.
 * Commitment has been recorded in a remote calendar for future attestation,
 * and we have a URI to find a more complete timestamp in the future.
 * Nothing other than the URI is recorded, nor is there provision made to add
 * extra metadata (other than the URI) in future upgrades. The rational here
 * is that remote calendars promise to keep commitments indefinitely, so from
 * the moment they are created it should be possible to find the commitment in
 * the calendar. Thus if you're not satisfied with the local verifiability of
 * a timestamp, the correct thing to do is just ask the remote calendar if
 * additional attestations are available and/or when they'll be available.
 * While we could additional metadata like what types of attestations the
 * remote calendar expects to be able to provide in the future, that metadata
 * can easily change in the future too. Given that we don't expect timestamps
 * to normally have more than a small number of remote calendar attestations,
 * it'd be better to have verifiers get the most recent status of such
 * information (possibly with appropriate negative response caching).
 *
 * @see TimeAttestation
 */
class PendingAttestation(
    val uri: ByteArray,
) : TimeAttestation() {
    override fun tag(): ByteArray = TAG

    override fun serializePayload(ctx: StreamSerializationContext) {
        ctx.writeVarbytes(this.uri)
    }

    override fun toString(): String = "PendingAttestation(\'" + String(this.uri, StandardCharsets.UTF_8) + "\')"

    override fun compareTo(other: TimeAttestation): Int {
        val opa = other as PendingAttestation

        return compare(this.uri, opa.uri)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is PendingAttestation) {
            return false
        }

        if (!this.tag().contentEquals(other.tag())) {
            return false
        }

        if (!this.uri.contentEquals(other.uri)) {
            return false
        }

        return true
    }

    override fun hashCode(): Int = this.tag().contentHashCode() xor this.uri.contentHashCode()

    companion object {
        val TAG: ByteArray =
            byteArrayOf(
                0x83.toByte(),
                0xdf.toByte(),
                0xe3.toByte(),
                0x0d.toByte(),
                0x2e.toByte(),
                0xf9.toByte(),
                0x0c.toByte(),
                0x8e.toByte(),
            )

        const val MAX_URI_LENGTH: Int = 1000

        const val ALLOWED_URI_CHARS: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._/:"

        fun checkUri(uri: ByteArray): Boolean {
            if (uri.size > MAX_URI_LENGTH) {
                System.err.print("URI exceeds maximum length")

                return false
            }

            for (i in uri.indices) {
                val c = String.format("%c", uri[i])[0]

                if (ALLOWED_URI_CHARS.indexOf(c) < 0) {
                    Log.e("OpenTimestamp", "URI contains invalid character ")

                    return false
                }
            }

            return true
        }

        @JvmStatic
        @Throws(DeserializationException::class)
        fun deserialize(ctxPayload: StreamDeserializationContext): PendingAttestation {
            val utf8Uri: ByteArray
            try {
                utf8Uri = ctxPayload.readVarbytes(MAX_URI_LENGTH)
            } catch (_: DeserializationException) {
                Log.e("OpenTimestamp", "URI too long and thus invalid: ")
                throw DeserializationException("Invalid URI: ")
            }

            if (!checkUri(utf8Uri)) {
                Log.e("OpenTimestamp", "Invalid URI: ")
                throw DeserializationException("Invalid URI: ")
            }

            return PendingAttestation(utf8Uri)
        }
    }
}

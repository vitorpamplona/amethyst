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
package com.vitorpamplona.quartz.nip03Timestamp

import android.util.Log
import com.vitorpamplona.quartz.nip03Timestamp.ots.BitcoinExplorer
import com.vitorpamplona.quartz.nip03Timestamp.ots.BlockstreamExplorer
import com.vitorpamplona.quartz.nip03Timestamp.ots.CalendarBuilder
import com.vitorpamplona.quartz.nip03Timestamp.ots.CalendarPureJavaBuilder
import com.vitorpamplona.quartz.nip03Timestamp.ots.DetachedTimestampFile
import com.vitorpamplona.quartz.nip03Timestamp.ots.Hash
import com.vitorpamplona.quartz.nip03Timestamp.ots.OpenTimestamps
import com.vitorpamplona.quartz.nip03Timestamp.ots.VerifyResult
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.UrlException
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpSHA256
import kotlinx.coroutines.CancellationException

class OtsResolver(
    explorer: BitcoinExplorer = BlockstreamExplorer(),
    calendarBuilder: CalendarBuilder = CalendarPureJavaBuilder(),
) {
    val ots: OpenTimestamps = OpenTimestamps(explorer, calendarBuilder)

    fun info(otsState: ByteArray): String = ots.info(DetachedTimestampFile.deserialize(otsState))

    fun stamp(data: ByteArray): ByteArray {
        val hash = Hash(data, OpSHA256._TAG)
        val file = DetachedTimestampFile.from(hash)
        val timestamp = ots.stamp(file)
        val detachedToSerialize = DetachedTimestampFile(hash.getOp(), timestamp)
        return detachedToSerialize.serialize()
    }

    fun upgrade(
        otsState: ByteArray,
        data: ByteArray,
    ): ByteArray? {
        val detachedOts = DetachedTimestampFile.deserialize(otsState)

        return if (ots.upgrade(detachedOts)) {
            // if the change is now verifiable.
            if (verify(detachedOts, data) is VerificationState.Verified) {
                detachedOts.serialize()
            } else {
                null
            }
        } else {
            null
        }
    }

    fun verify(
        otsFile: ByteArray,
        data: ByteArray,
    ): VerificationState = verify(DetachedTimestampFile.deserialize(otsFile), data)

    fun verify(
        detachedOts: DetachedTimestampFile,
        data: ByteArray,
    ): VerificationState {
        try {
            val result = ots.verify(detachedOts, data)
            if (result == null || result.isEmpty()) {
                return VerificationState.Error("Verification hashmap is empty")
            } else {
                val time = result.get(VerifyResult.Chains.BITCOIN)?.timestamp
                return if (time != null) {
                    VerificationState.Verified(time)
                } else {
                    VerificationState.Error("Does not include a Bitcoin verification")
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("OpenTimeStamps", "Failed to verify", e)
            return if (e is UrlException) {
                VerificationState.NetworkError(e.message ?: e.cause?.message ?: "Failed to verify")
            } else {
                VerificationState.Error(e.message ?: e.cause?.message ?: "Failed to verify")
            }
        }
    }
}

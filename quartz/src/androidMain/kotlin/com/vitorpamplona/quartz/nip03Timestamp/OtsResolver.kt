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
package com.vitorpamplona.quartz.nip03Timestamp

import android.util.Log
import com.vitorpamplona.quartz.nip03Timestamp.ots.BitcoinExplorer
import com.vitorpamplona.quartz.nip03Timestamp.ots.CalendarBuilder
import com.vitorpamplona.quartz.nip03Timestamp.ots.DetachedTimestampFile
import com.vitorpamplona.quartz.nip03Timestamp.ots.Hash
import com.vitorpamplona.quartz.nip03Timestamp.ots.OpenTimestamps
import com.vitorpamplona.quartz.nip03Timestamp.ots.VerifyResult
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.UrlException
import com.vitorpamplona.quartz.nip03Timestamp.ots.http.BlockstreamExplorer
import com.vitorpamplona.quartz.nip03Timestamp.ots.http.CalendarPureJavaBuilder
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpSHA256
import kotlinx.coroutines.CancellationException

/**
 * Resolver class for OpenTimestamps operations.
 *
 * This class provides functionality to stamp data with OpenTimestamps,
 * upgrade existing timestamps, and verify timestamp proofs against data.
 *
 * @property explorer The Bitcoin explorer used for verification operations.
 * @property calendarBuilder The calendar builder used for stamping operations.
 */
class OtsResolver(
    explorer: BitcoinExplorer = BlockstreamExplorer(),
    calendarBuilder: CalendarBuilder = CalendarPureJavaBuilder(),
) {
    val ots: OpenTimestamps = OpenTimestamps(explorer, calendarBuilder)

    /**
     * Returns a human-readable information string about the given OTS file.
     *
     * @param otsState The serialized OpenTimestamps file data.
     * @return A string containing information about the timestamp.
     */
    fun info(otsState: ByteArray): String = ots.info(DetachedTimestampFile.deserialize(otsState))

    /**
     * Creates a local timestamp proof for the given data.
     *
     * @param data The data to be timestamped.
     * @return A serialized DetachedTimestampFile containing the timestamp proof.
     */
    fun stamp(data: ByteArray): ByteArray {
        val hash = Hash(data, OpSHA256.TAG)
        val file = DetachedTimestampFile.from(hash)
        val timestamp = ots.stamp(file)
        val detachedToSerialize = DetachedTimestampFile(hash.op, timestamp)
        return detachedToSerialize.serialize()
    }

    /**
     * Attempts to upgrade an existing timestamp to a verified proof.
     *
     * @param otsState The serialized OpenTimestamps file data to upgrade.
     * @param data The original data that was timestamped.
     * @return A serialized DetachedTimestampFile with upgraded proof if successful, null otherwise.
     */
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

    /**
     * Verifies an OpenTimestamps proof against the original data.
     *
     * @param otsFile The serialized OpenTimestamps file data.
     * @param data The original data to verify against.
     * @return VerificationState indicating the result of the verification.
     */
    fun verify(
        otsFile: ByteArray,
        data: ByteArray,
    ): VerificationState = verify(DetachedTimestampFile.deserialize(otsFile), data)

    /**
     * Verifies an OpenTimestamps proof against the original data.
     *
     * @param detachedOts The DetachedTimestampFile containing the proof.
     * @param data The original data to verify against.
     * @return VerificationState indicating the result of the verification.
     */
    fun verify(
        detachedOts: DetachedTimestampFile,
        data: ByteArray,
    ): VerificationState {
        try {
            val result = ots.verify(detachedOts, data)
            if (result.isEmpty()) {
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
            Log.w("OpenTimeStamps", "Failed to verify", e)
            return if (e is UrlException) {
                VerificationState.NetworkError(e.message ?: e.cause?.message ?: "Failed to verify")
            } else {
                VerificationState.Error(e.message ?: e.cause?.message ?: "Failed to verify")
            }
        }
    }
}

/*
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
package com.vitorpamplona.quartz.nip03Timestamp.ots

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip03Timestamp.ots.VerifyResult.Chains
import com.vitorpamplona.quartz.nip03Timestamp.ots.attestation.BitcoinBlockHeaderAttestation
import com.vitorpamplona.quartz.nip03Timestamp.ots.attestation.PendingAttestation
import com.vitorpamplona.quartz.nip03Timestamp.ots.attestation.TimeAttestation
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.VerificationException
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpAppend
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpCrypto
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpSHA256
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.mapNotNullAsync
import kotlinx.coroutines.CancellationException

/**
 * The main class for timestamp operations.
 */
class OpenTimestamps(
    var explorer: BitcoinExplorer,
    var calendar: RemoteCalendar,
) {
    /**
     * Show information on a detached timestamp with verbose option.
     *
     * @param detachedTimestampFile The DetachedTimestampFile ots.
     * @param verbose               Show verbose output.
     * @return the string representation of the timestamp.
     */
    fun info(
        detachedTimestampFile: DetachedTimestampFile?,
        verbose: Boolean = false,
    ): String {
        if (detachedTimestampFile == null) {
            return "No ots file"
        }

        val fileHash =
            detachedTimestampFile.timestamp.digest
                .toHexKey()
                .lowercase()
        val hashOp = (detachedTimestampFile.fileHashOp as OpCrypto).tagName()

        val firstLine = "File $hashOp hash: $fileHash\n"

        return firstLine + "Timestamp:\n" + detachedTimestampFile.timestamp.strTree(0, verbose)
    }

    /**
     * Show information on a timestamp.
     *
     * @param timestamp The timestamp buffered.
     * @return the string representation of the timestamp.
     */
    fun info(timestamp: Timestamp?): String {
        if (timestamp == null) {
            return "No timestamp"
        }

        val fileHash = timestamp.digest.toHexKey().lowercase()
        val firstLine = "Hash: $fileHash\n"

        return firstLine + "Timestamp:\n" + timestamp.strTree(0)
    }

    /**
     * Create timestamp with the aid of a remote calendar. May be specified multiple times.
     *
     * @param fileTimestamp       The timestamp to stamp.
     * @param calendarsUrl        The list of calendar urls.
     * @return The plain array buffer of stamped.
     * @throws IOException if fileTimestamp is not valid, or the stamp procedure fails.
     */
    suspend fun stamp(
        fileTimestamp: DetachedTimestampFile?,
        calendarsUrl: MutableList<String> = mutableListOf(),
    ): Timestamp {
        val fileTimestamps: MutableList<DetachedTimestampFile> = ArrayList<DetachedTimestampFile>()
        fileTimestamps.add(fileTimestamp!!)

        return stamp(fileTimestamps, calendarsUrl)
    }

    /**
     * Create timestamp with the aid of a remote calendar. May be specified multiple times.
     *
     * @param fileTimestamps      The list of timestamp to stamp.
     * @param calendarsUrl        The list of calendar urls.
     * @return The plain array buffer of stamped.
     * @throws IllegalArgumentException if fileTimestamp is not valid, or the stamp procedure fails.
     */
    suspend fun stamp(
        fileTimestamps: MutableList<DetachedTimestampFile>,
        calendarsUrl: MutableList<String>,
    ): Timestamp {
        // Parse parameters
        var calendarsUrl = calendarsUrl
        if (fileTimestamps.isEmpty()) {
            throw IllegalArgumentException()
        }

        if (calendarsUrl.isEmpty()) {
            calendarsUrl = ArrayList()
            calendarsUrl.add("https://alice.btc.calendar.opentimestamps.org")
            calendarsUrl.add("https://bob.btc.calendar.opentimestamps.org")
            calendarsUrl.add("https://finney.calendar.eternitywall.com")
        }

        // Build markle tree
        val merkleTip = makeMerkleTree(fileTimestamps)

        // Stamping
        val resultTimestamp = create(merkleTip, calendarsUrl)

        // Result of timestamp serialization
        if (fileTimestamps.size == 1) {
            return fileTimestamps.get(0).timestamp
        } else {
            return merkleTip
        }
    }

    /**
     * Create a timestamp
     *
     * @param timestamp    The timestamp.
     * @param calendarUrls List of calendar's to use.
     * @param m            Number of calendars to use.
     * @return The created timestamp.
     */
    private suspend fun create(
        timestamp: Timestamp,
        calendarUrls: MutableList<String>,
    ): Timestamp {
        val responses =
            mapNotNullAsync(calendarUrls) { calendarUrl ->
                Log.i("OpenTimestamps", "Submitting to remote calendar $calendarUrl")
                calendar.submit(calendarUrl, timestamp.digest)
            }

        responses.forEach {
            timestamp.merge(it)
        }

        return timestamp
    }

    /**
     * Make Merkle Tree of detached timestamps.
     *
     * @param fileTimestamps The list of DetachedTimestampFile.
     * @return merkle tip timestamp.
     */
    fun makeMerkleTree(fileTimestamps: MutableList<DetachedTimestampFile>): Timestamp {
        val merkleRoots: MutableList<Timestamp> = ArrayList()

        for (fileTimestamp in fileTimestamps) {
            val bytesRandom16 = RandomInstance.bytes(16)
            val nonceAppendedStamp = fileTimestamp.timestamp.add(OpAppend(bytesRandom16))
            val merkleRoot = nonceAppendedStamp.add(OpSHA256())
            merkleRoots.add(merkleRoot)
        }

        val merkleTip: Timestamp = Merkle.makeMerkleTree(merkleRoots)!!
        return merkleTip
    }

    /**
     * Compare and verify a detached timestamp.
     *
     * @param ots     The DetachedTimestampFile containing the proof to verify.
     * @param diggest The hash of the stamped file, in bytes
     * @return Hashmap of block heights and timestamps indexed by chain: timestamp in seconds from 1 January 1970.
     * @throws Exception if the verification procedure fails.
     */
    @Throws(Exception::class)
    suspend fun verify(
        ots: DetachedTimestampFile,
        diggest: ByteArray,
    ): HashMap<Chains, VerifyResult> {
        if (!ots.fileDigest().contentEquals(diggest)) {
            Log.e("OpenTimestamp", "Expected digest " + Hex.encode(ots.fileDigest()!!).lowercase())
            Log.e("OpenTimestamp", "File does not match original!")
            throw Exception("File does not match original!")
        }

        return verify(ots.timestamp)
    }

    /**
     * Verify a timestamp.
     *
     * @param timestamp The timestamp.
     * @return HashMap of block heights and timestamps indexed by chain: timestamp in seconds from 1 January 1970.
     * @throws Exception if the verification procedure fails.
     */
    @Throws(Exception::class)
    suspend fun verify(timestamp: Timestamp): HashMap<Chains, VerifyResult> {
        val verifyResults = HashMap<Chains, VerifyResult>()

        for (item in timestamp.allAttestations().entries) {
            val msg = item.key
            val attestation: TimeAttestation? = item.value
            var verifyResult: VerifyResult? = null

            try {
                if (attestation is BitcoinBlockHeaderAttestation) {
                    val time = verify(attestation, msg)
                    val height = attestation.height
                    verifyResult = VerifyResult(time, height)

                    val currentResults = verifyResults.get(Chains.BITCOIN)

                    if (currentResults != null) {
                        if (verifyResult.height < currentResults.height) {
                            verifyResults.put(Chains.BITCOIN, verifyResult)
                        }
                    } else {
                        verifyResults.put(Chains.BITCOIN, verifyResult)
                    }
                }
            } catch (e: VerificationException) {
                throw e
            } catch (e: Exception) {
                Log.e("OpenTimestamp", "Verification failed: " + e.message)
                throw e
            }
        }
        return verifyResults
    }

    /**
     * Verify an Bitcoin Block Header Attestation.
     * if the node is not reachable or it fails, uses Lite-client verification.
     *
     * @param attestation The BitcoinBlockHeaderAttestation attestation.
     * @param msg         The digest to verify.
     * @return The unix timestamp in seconds from 1 January 1970.
     * @throws VerificationException if it doesn't check the merkle root of the block.
     * @throws Exception             if the verification procedure fails.
     */
    @Throws(VerificationException::class, Exception::class)
    suspend fun verify(
        attestation: BitcoinBlockHeaderAttestation,
        msg: ByteArray,
    ): Long? {
        val height = attestation.height
        val blockInfo: BlockHeader?

        try {
            val blockHash = explorer.blockHash(height)
            blockInfo = explorer.block(blockHash)
            Log.i(
                "OpenTimestamps",
                "Lite-client verification, assuming block $blockHash is valid",
            )
        } catch (e2: Exception) {
            e2.printStackTrace()
            throw e2
        }

        return attestation.verifyAgainstBlockheader(msg.reversedArray(), blockInfo)
    }

    /**
     * Upgrade a timestamp.
     *
     * @param detachedTimestamp The DetachedTimestampFile containing the proof to verify.
     * @return a boolean representing if the timestamp has changed.
     * @throws Exception if the upgrading procedure fails.
     */
    @Throws(Exception::class)
    suspend fun upgrade(detachedTimestamp: DetachedTimestampFile): Boolean {
        // Upgrade timestamp
        val changed = upgrade(detachedTimestamp.timestamp)
        return changed
    }

    /**
     * Attempt to upgrade an incomplete timestamp to make it verifiable.
     * Note that this means if the timestamp that is already complete, False will be returned as nothing has changed.
     *
     * @param timestamp The timestamp to upgrade.
     * @return a boolean representing if the timestamp has changed.
     * @throws Exception if the upgrading procedure fails.
     */
    @Throws(Exception::class)
    suspend fun upgrade(timestamp: Timestamp): Boolean {
        // Check remote calendars for upgrades.
        // This time we only check PendingAttestations - we can't be as agressive.

        var upgraded = false
        val existingAttestations: MutableSet<TimeAttestation> = timestamp.getAttestations()

        for (subStamp in timestamp.directlyVerified()) {
            for (attestation in subStamp.attestations) {
                if (attestation is PendingAttestation && !subStamp.isTimestampComplete) {
                    val calendarUrl = attestation.uri.decodeToString()
                    // var calendarUrl = calendarUrls[0];
                    val commitment = subStamp.digest

                    try {
                        val upgradedStamp = upgrade(subStamp, calendarUrl, commitment, existingAttestations)

                        try {
                            subStamp.merge(upgradedStamp)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            e.printStackTrace()
                        }

                        upgraded = true
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w("OpenTimestamps", e.message ?: "", e)
                    }
                }
            }
        }

        return upgraded
    }

    @Throws(Exception::class)
    private suspend fun upgrade(
        subStamp: Timestamp,
        calendarUrl: String,
        commitment: ByteArray,
        existingAttestations: MutableSet<TimeAttestation>,
    ): Timestamp {
        val upgradedStamp: Timestamp =
            try {
                calendar.getTimestamp(calendarUrl, commitment)
            } catch (e: Exception) {
                Log.i("OpenTimestamps", "Calendar " + calendarUrl + ": " + e.message)
                throw e
            }

        val attsFromRemote: MutableSet<TimeAttestation> = upgradedStamp.getAttestations()

        if (attsFromRemote.isNotEmpty()) {
            Log.i("OpenTimestamps", "Got 1 attestation(s) from $calendarUrl")
        }

        // Set difference from remote attestations & existing attestations
        val newAttestations = attsFromRemote.minus(existingAttestations)

        // changed & found_new_attestations
        // foundNewAttestations = true;
        // Log.i("OpenTimestamps", attsFromRemote.size + ' attestation(s) from ' + calendar.url);

        // Set union of existingAttestations & newAttestations
        existingAttestations.addAll(newAttestations)

        return upgradedStamp
    }
}

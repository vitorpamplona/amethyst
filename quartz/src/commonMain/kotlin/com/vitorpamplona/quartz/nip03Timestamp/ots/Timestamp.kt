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
import com.vitorpamplona.quartz.nip03Timestamp.ots.attestation.BitcoinBlockHeaderAttestation
import com.vitorpamplona.quartz.nip03Timestamp.ots.attestation.TimeAttestation
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.DeserializationException
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.Op
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpBinary
import com.vitorpamplona.quartz.utils.Hex.encode
import kotlin.jvm.JvmOverloads

/**
 * Proof that one or more attestations commit to a message.
 * The proof is in the form of a tree, with each node being a message, and the
 * edges being operations acting on those messages. The leafs of the tree are
 * attestations that attest to the time that messages in the tree existed prior.
 */
class Timestamp(
    val digest: ByteArray,
) {
    val attestations: MutableList<TimeAttestation> = ArrayList()
    val ops: MutableMap<Op, Timestamp> = mutableMapOf<Op, Timestamp>()

    /**
     * Create a Serialize object.
     *
     * @return The byte array of the serialized timestamp
     */
    fun serialize(): ByteArray {
        val ctx = StreamSerializationContext()
        serialize(ctx)

        return ctx.output
    }

    /**
     * Create a Serialize object.
     *
     * @param ctx - The stream serialization context.
     */
    fun serialize(ctx: StreamSerializationContext) {
        this.attestations.sort()

        if (this.attestations.size > 1) {
            for (i in 0..<this.attestations.size - 1) {
                ctx.writeBytes(byteArrayOf(0xff.toByte(), 0x00.toByte()))
                this.attestations.get(i).serialize(ctx)
            }
        }

        if (this.ops.isEmpty()) {
            ctx.writeByte(0x00.toByte())

            if (!this.attestations.isEmpty()) {
                this.attestations.get(this.attestations.size - 1).serialize(ctx)
            }
        } else if (!this.ops.isEmpty()) {
            if (!this.attestations.isEmpty()) {
                ctx.writeBytes(byteArrayOf(0xff.toByte(), 0x00.toByte()))
                this.attestations.get(this.attestations.size - 1).serialize(ctx)
            }

            var counter = 0
            val list = sortToList(this.ops.entries)

            for (entry in list) {
                val stamp = entry.value
                val op = entry.key

                if (counter < this.ops.size - 1) {
                    ctx.writeBytes(byteArrayOf(0xff.toByte()))
                    counter++
                }

                op.serialize(ctx)
                stamp.serialize(ctx)
            }
        }
    }

    /**
     * Add all operations and attestations from another timestamp to this one.
     *
     * @param other - Initial other com.vitorpamplona.quartz.ots.Timestamp to merge.
     * @throws Exception different timestamps messages
     */
    @Throws(Exception::class)
    fun merge(other: Timestamp) {
        if (!this.digest.contentEquals(other.digest)) {
            // Log.e("OpenTimestamp", "Can\'t merge timestamps for different messages together");
            throw Exception("Can\'t merge timestamps for different messages together")
        }

        for (attestation in other.attestations) {
            this.attestations.add(attestation)
        }

        for (entry in other.ops.entries) {
            val otherOpStamp: Timestamp = entry.value
            val otherOp: Op = entry.key

            var ourOpStamp = this.ops.get(otherOp)

            if (ourOpStamp == null) {
                ourOpStamp = Timestamp(otherOp.call(this.digest))
                this.ops.put(otherOp, ourOpStamp)
            }

            ourOpStamp.merge(otherOpStamp)
        }
    }

    /**
     * Shrink Timestamp.
     * Remove useless pending attestions if exist a full bitcoin attestation.
     *
     * @return TimeAttestation - the minimal attestation.
     * @throws Exception no attestion founds.
     */
    @Throws(Exception::class)
    fun shrink(): TimeAttestation? {
        // Get all attestations
        val allAttestations = this.allAttestations()

        if (allAttestations.isEmpty()) {
            throw Exception()
        } else if (allAttestations.size == 1) {
            return allAttestations.values.iterator().next()
        } else if (this.ops.isEmpty()) {
            throw Exception() // TODO: Need a descriptive exception string here
        }

        // Fore >1 attestations :
        // Search first BitcoinBlockHeaderAttestation
        var minAttestation: TimeAttestation? = null

        for (entry in this.ops.entries) {
            val timestamp: Timestamp = entry.value

            // Op op = entry.getKey();
            for (attestation in timestamp.getAttestations()) {
                if (attestation is BitcoinBlockHeaderAttestation) {
                    if (minAttestation == null) {
                        minAttestation = attestation
                    } else {
                        if (minAttestation is BitcoinBlockHeaderAttestation && (minAttestation.height > attestation.height)) {
                            minAttestation = attestation
                        }
                    }
                }
            }
        }

        // Only pending attestations : return the first
        if (minAttestation == null) {
            return allAttestations.values.iterator().next()
        }

        // Remove attestation if not min attestation
        var shrinked = false

        val it = this.ops.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            val timestamp = entry.value
            val op = entry.key
            val attestations = timestamp.getAttestations()

            if (attestations.isNotEmpty() && attestations.contains(minAttestation) && !shrinked) {
                timestamp.shrink()
                shrinked = true
            } else {
                it.remove()
            }
        }

        return minAttestation
    }

    /**
     * Return as memory hierarchical object.
     *
     * @param indent - Initial hierarchical indention.
     * @return The output string.
     */
    fun toString(indent: Int): String {
        val builder = StringBuilder()
        builder.append(indention(indent) + "msg: " + encode(this.digest) + "\n")
        builder.append(indention(indent) + this.attestations.size + " attestations: \n")
        var i = 0

        for (attestation in this.attestations) {
            builder.append(indention(indent) + "[" + i + "] " + attestation.toString() + "\n")
            i++
        }

        i = 0
        builder.append(indention(indent) + this.ops.size + " ops: \n")

        for (entry in this.ops.entries) {
            val stamp: Timestamp = entry.value
            val op: Op = entry.key

            builder.append(indention(indent) + "[" + i + "] op: " + op.toString() + "\n")
            builder.append(indention(indent) + "[" + i + "] timestamp: \n")
            builder.append(stamp.toString(indent + 1))
            i++
        }

        builder.append('\n')

        return builder.toString()
    }

    private fun strResult(
        verbosity: Boolean,
        parameter: ByteArray?,
        result: ByteArray?,
    ): String {
        var rr = ""

        if (verbosity == true && result != null) {
            rr += " == "
            val resultHex = result.toHexKey()

            if (parameter == null) {
                rr += resultHex
            } else {
                val parameterHex = parameter.toHexKey()

                try {
                    val index = resultHex.indexOf(parameterHex)
                    val parameterHexHighlight = ANSI_BOLD + parameterHex + ANSI_ENDC

                    if (index == 0) {
                        rr += parameterHexHighlight +
                            resultHex.substring(
                                index + parameterHex.length,
                                resultHex.length,
                            )
                    } else {
                        rr += resultHex.substring(0, index) + parameterHexHighlight
                    }
                } catch (err: Exception) {
                    rr += resultHex
                }
            }
        }

        return rr
    }

    /**
     * Return as tree hierarchical object.
     *
     * @param indent    - Initial hierarchical indention.
     * @param verbosity - Verbose option.
     * @return The output string.
     */
    @JvmOverloads
    fun strTree(
        indent: Int,
        verbosity: Boolean = false,
    ): String {
        val builder = StringBuilder()

        if (!this.attestations.isEmpty()) {
            for (attestation in this.attestations) {
                builder.append(indention(indent))
                builder.append(
                    "verify " + attestation.toString() +
                        strResult(
                            verbosity,
                            this.digest,
                            null,
                        ) + "\n",
                )

                if (attestation is BitcoinBlockHeaderAttestation) {
                    val tx = this.digest.reversedArray().toHexKey()
                    builder.append(indention(indent) + "# Bitcoin block merkle root " + tx.lowercase() + "\n")
                }
            }
        }

        if (this.ops.size > 1) {
            val sortedKeys = this.ops.entries.sortedBy { it.key }
            val ordered = sortedKeys.associate { it.toPair() }

            for (entry in ordered.entries) {
                val timestamp: Timestamp = entry.value
                val op: Op = entry.key

                val curRes = op.call(this.digest)
                var curPar: ByteArray? = null

                if (op is OpBinary) {
                    curPar = op.arg
                }

                builder.append(
                    indention(indent) + " -> " + op.toString().lowercase() +
                        strResult(
                            verbosity,
                            curPar,
                            curRes,
                        ).lowercase() + "\n",
                )
                builder.append(timestamp.strTree(indent + 1, verbosity))
            }
        } else if (this.ops.size > 0) {
            // output += com.eternitywall.ots.Timestamp.indention(indent);
            for (entry in this.ops.entries) {
                val timestamp: Timestamp = entry.value
                val op: Op = entry.key

                val curRes = op.call(this.digest)
                var curPar: ByteArray? = null

                if (op is OpBinary) {
                    curPar = op.arg
                }

                builder.append(
                    indention(indent) + op.toString().lowercase() +
                        strResult(
                            verbosity,
                            curPar,
                            curRes,
                        ).lowercase() + "\n",
                )
                builder.append(timestamp.strTree(indent, verbosity))
            }
        }

        return builder.toString()
    }

    /**
     * Returns a list of all sub timestamps with attestations.
     *
     * @return List of all sub timestamps with attestations.
     */
    fun directlyVerified(): MutableList<Timestamp> {
        if (!this.attestations.isEmpty()) {
            val list: MutableList<Timestamp> = ArrayList<Timestamp>()
            list.add(this)
            return list
        }

        val list: MutableList<Timestamp> = ArrayList<Timestamp>()

        for (entry in this.ops.entries) {
            val ts: Timestamp = entry.value

            // Op op = entry.getKey();
            val result = ts.directlyVerified()
            list.addAll(result)
        }

        return list
    }

    /**
     * Returns a set of all Attestations.
     *
     * @return Set of all timestamp attestations.
     */
    fun getAttestations(): MutableSet<TimeAttestation> {
        val set: MutableSet<TimeAttestation> = HashSet()

        for (item in this.allAttestations().entries) {
            // byte[] msg = item.getKey();
            val attestation = item.value
            set.add(attestation)
        }

        return set
    }

    val isTimestampComplete: Boolean
        /**
         * Determine if timestamp is complete and can be verified.
         *
         * @return True if the timestamp is complete, False otherwise.
         */
        get() {
            for (item in this.allAttestations().entries) {
                // byte[] msg = item.getKey();
                val attestation = item.value

                if (attestation is BitcoinBlockHeaderAttestation) {
                    return true
                }
            }

            return false
        }

    /**
     * Iterate over all attestations recursively
     *
     * @return Returns iterable of (msg, attestation)
     */
    fun allAttestations(): HashMap<ByteArray, TimeAttestation> {
        val map = HashMap<ByteArray, TimeAttestation>()

        for (attestation in this.attestations) {
            map.put(this.digest, attestation)
        }

        for (entry in this.ops.entries) {
            val ts: Timestamp = entry.value

            // Op op = entry.getKey();
            val subMap = ts.allAttestations()

            for (item in subMap.entries) {
                val msg = item.key
                val attestation = item.value
                map.put(msg, attestation)
            }
        }

        return map
    }

    /**
     * Iterate over all tips recursively
     *
     * @return Returns iterable of (msg, attestation)
     */
    fun allTips(): MutableSet<ByteArray?> {
        val set: MutableSet<ByteArray?> = HashSet<ByteArray?>()

        if (this.ops.isEmpty()) {
            set.add(this.digest)
        }

        for (entry in this.ops.entries) {
            val ts: Timestamp = entry.value

            // Op op = entry.getKey();
            val subSet = ts.allTips()

            for (msg in subSet) {
                set.add(msg)
            }
        }

        return set
    }

    /**
     * Compare timestamps.
     *
     * @param timestamp the timestamp to compare with
     * @return Returns true if timestamps are equals
     */
    fun equals(timestamp: Timestamp): Boolean {
        if (this.digest.contentEquals(timestamp.digest) == false) {
            return false
        }

        // Check attestations
        if (this.attestations.size != timestamp.attestations.size) {
            return false
        }

        for (i in this.attestations.indices) {
            val ta1 = this.attestations.get(i)
            val ta2 = timestamp.attestations.get(i)

            if (!(ta1 == ta2)) {
                return false
            }
        }

        // Check operations
        if (this.ops.size != timestamp.ops.size) {
            return false
        }

        // Order list of operations
        val list1 = sortToList(this.ops.entries)
        val list2 = sortToList(timestamp.ops.entries)

        for (i in list1.indices) {
            val op1 = list1.get(i).key
            val op2: Op? = list2.get(i).key

            if (op1 != op2) {
                return false
            }

            val t1 = list1.get(i).value
            val t2 = list2.get(i).value

            if (!t1.equals(t2)) {
                return false
            }
        }

        return true
    }

    /**
     * Add Op to current timestamp and return the sub stamp
     *
     * @param op - The operation to insert
     * @return Returns the sub timestamp
     */
    fun add(op: Op): Timestamp {
        // nonce_appended_stamp = timestamp.ops.add(com.vitorpamplona.quartz.ots.op.OpAppend(os.urandom(16)))
        // Op opAppend = new OpAppend(bytes);

        val existing = this.ops.get(op)

        if (existing != null) {
            return existing
        }

        val stamp = Timestamp(op.call(this.digest))
        this.ops.put(op, stamp)

        return stamp
    }

    /**
     * Retrieve a sorted list of all map entries.
     *
     * @param setEntries - The entries set of ops hashmap
     * @return Returns the sorted list of map entries
     */
    fun sortToList(setEntries: MutableSet<MutableMap.MutableEntry<Op, Timestamp>>): List<MutableMap.MutableEntry<Op, Timestamp>> =
        setEntries.sortedWith(
            Comparator<MutableMap.MutableEntry<Op, Timestamp>> { a, b ->
                a.key.compareTo(b.key)
            },
        )

    companion object {
        val ANSI_HEADER = "\u001B[95m"
        val ANSI_OKBLUE = "\u001B[94m"
        val ANSI_OKGREEN = "\u001B[92m"
        val ANSI_WARNING = "\u001B[93m"
        val ANSI_FAIL = "\u001B[91m"
        val ANSI_ENDC = "\u001B[0m"
        val ANSI_BOLD = "\u001B[1m"
        val ANSI_UNDERLINE = "\u001B[4m"

        /**
         * Deserialize a Timestamp.
         *
         * @param ots        - The serialized byte array.
         * @param initialMsg - The initial message.
         * @return The deserialized Timestamp.
         */
        @Throws(DeserializationException::class)
        fun deserialize(
            ots: ByteArray,
            initialMsg: ByteArray,
        ): Timestamp {
            val ctx = StreamDeserializationContext(ots)

            return deserialize(ctx, initialMsg)
        }

        /**
         * Deserialize a Timestamp.
         * Because the serialization format doesn't include the message that the
         * timestamp operates on, you have to provide it so that the correct
         * operation results can be calculated.
         * The message you provide is assumed to be correct; if it causes a op to
         * raise MsgValueError when the results are being calculated (done
         * immediately, not lazily) DeserializationError is raised instead.
         *
         * @param ctx        - The stream deserialization context.
         * @param initialMsg - The initial message.
         * @return The deserialized Timestamp.
         */

        @Throws(DeserializationException::class)
        fun deserialize(
            ctx: StreamDeserializationContext,
            initialMsg: ByteArray,
        ): Timestamp {
            val self = Timestamp(initialMsg)
            var tag = ctx.readBytes(1)[0]

            while ((tag.toInt() and 0xff) == 0xff) {
                val current = ctx.readBytes(1)[0]
                doTagOrAttestation(self, ctx, current, initialMsg)
                tag = ctx.readBytes(1)[0]
            }

            doTagOrAttestation(self, ctx, tag, initialMsg)

            return self
        }

        @Throws(DeserializationException::class)
        private fun doTagOrAttestation(
            self: Timestamp,
            ctx: StreamDeserializationContext,
            tag: Byte,
            initialMsg: ByteArray,
        ) {
            if ((tag.toInt() and 0xff) == 0x00) {
                val attestation = TimeAttestation.deserialize(ctx)
                self.attestations.add(attestation)
            } else {
                val op = Op.deserializeFromTag(ctx, tag)
                val result = op!!.call(initialMsg)

                val stamp: Timestamp = deserialize(ctx, result)
                self.ops.put(op, stamp)
            }
        }

        /**
         * Indention function for printing tree.
         *
         * @param pos - Initial hierarchical indention.
         * @return The output space string.
         */
        fun indention(pos: Int): String {
            val builder = StringBuilder()

            for (i in 0..<pos) {
                builder.append("    ")
            }

            return builder.toString()
        }
    }
}

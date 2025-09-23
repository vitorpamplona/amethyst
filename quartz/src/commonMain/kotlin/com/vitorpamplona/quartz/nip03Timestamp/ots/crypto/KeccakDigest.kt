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
package com.vitorpamplona.quartz.nip03Timestamp.ots.crypto

/**
 * Implementation of Keccak based on following KeccakNISTInterface.c from http://keccak.noekeon.org/
 *
 *
 * Following the naming conventions used in the C source code to enable easy review of the implementation.
 */
class KeccakDigest : ExtendedDigest {
    var state: ByteArray = ByteArray((1600 / 8))
    var dataQueue: ByteArray = ByteArray((1536 / 8))
    var rate: Int = 0
    var bitsInQueue: Int = 0
    var fixedOutputLength: Int = 0
    var squeezing: Boolean = false
    var bitsAvailableForSqueezing: Int = 0
    var chunk: ByteArray = byteArrayOf()
    var oneByte: ByteArray = byteArrayOf()

    private fun clearDataQueueSection(
        off: Int,
        len: Int,
    ) {
        for (i in off..<off + len) {
            dataQueue[i] = 0
        }
    }

    constructor(bitLength: Int = 288) {
        init(bitLength)
    }

    constructor(source: KeccakDigest) {
        source.state.copyInto(this.state, 0, 0, source.state.size)
        source.dataQueue.copyInto(this.dataQueue, 0, 0, source.dataQueue.size)
        this.rate = source.rate
        this.bitsInQueue = source.bitsInQueue
        this.fixedOutputLength = source.fixedOutputLength
        this.squeezing = source.squeezing
        this.bitsAvailableForSqueezing = source.bitsAvailableForSqueezing
        this.chunk = source.chunk.copyOf()
        this.oneByte = source.oneByte.copyOf()
    }

    override fun getAlgorithmName(): String = "Keccak-$fixedOutputLength"

    override fun getDigestSize(): Int = fixedOutputLength / 8

    override fun update(`in`: Byte) {
        oneByte[0] = `in`

        absorb(oneByte, 0, 8L)
    }

    override fun update(
        `in`: ByteArray,
        inOff: Int,
        len: Int,
    ) {
        absorb(`in`, inOff, len * 8L)
    }

    override fun doFinal(
        out: ByteArray,
        outOff: Int,
    ): Int {
        squeeze(out, outOff, fixedOutputLength.toLong())
        reset()

        return getDigestSize()
    }

    /*
     * TODO Possible API change to support partial-byte suffixes.
     */
    fun doFinal(
        out: ByteArray,
        outOff: Int,
        partialByte: Byte,
        partialBits: Int,
    ): Int {
        if (partialBits > 0) {
            oneByte[0] = partialByte
            absorb(oneByte, 0, partialBits.toLong())
        }

        squeeze(out, outOff, fixedOutputLength.toLong())
        reset()

        return getDigestSize()
    }

    override fun reset() {
        init(fixedOutputLength)
    }

    /**
     * Return the size of block that the compression function is applied to in bytes.
     *
     * @return internal byte length of a block.
     */
    override fun getByteLength(): Int = rate / 8

    private fun init(bitLength: Int) {
        when (bitLength) {
            288 -> initSponge(1024, 576)
            128 -> initSponge(1344, 256)
            224 -> initSponge(1152, 448)
            256 -> initSponge(1088, 512)
            384 -> initSponge(832, 768)
            512 -> initSponge(576, 1024)
            else -> throw IllegalArgumentException("bitLength must be one of 128, 224, 256, 288, 384, or 512.")
        }
    }

    private fun initSponge(
        rate: Int,
        capacity: Int,
    ) {
        check(rate + capacity == 1600) { "rate + capacity != 1600" }

        check(!((rate <= 0) || (rate >= 1600) || ((rate % 64) != 0))) { "invalid rate value" }

        this.rate = rate
        // this is never read, need to check to see why we want to save it
        //  this.capacity = capacity;
        this.state.fill(0.toByte())
        this.dataQueue.fill(0.toByte())
        this.bitsInQueue = 0
        this.squeezing = false
        this.bitsAvailableForSqueezing = 0
        this.fixedOutputLength = capacity / 2
        this.chunk = ByteArray(rate / 8)
        this.oneByte = ByteArray(1)
    }

    private fun absorbQueue() {
        keccakAbsorb(state, dataQueue, rate / 8)
        bitsInQueue = 0
    }

    fun absorb(
        data: ByteArray,
        off: Int,
        databitlen: Long,
    ) {
        var i: Long
        var j: Long
        var wholeBlocks: Long

        check((bitsInQueue % 8) == 0) { "attempt to absorb with odd length queue" }

        check(!squeezing) { "attempt to absorb while squeezing" }

        i = 0

        while (i < databitlen) {
            if ((bitsInQueue == 0) && (databitlen >= rate) && (i <= (databitlen - rate))) {
                wholeBlocks = (databitlen - i) / rate

                j = 0
                while (j < wholeBlocks) {
                    val index = (off + (i / 8) + (j * chunk.size)).toInt()
                    data.copyInto(
                        destination = chunk,
                        destinationOffset = 0,
                        startIndex = index,
                        endIndex = index + chunk.size,
                    )

                    //                            displayIntermediateValues.displayBytes(1, "Block to be absorbed", curData, rate / 8);
                    keccakAbsorb(state, chunk, chunk.size)
                    j++
                }

                i += wholeBlocks * rate
            } else {
                var partialBlock = (databitlen - i).toInt()

                if (partialBlock + bitsInQueue > rate) {
                    partialBlock = rate - bitsInQueue
                }

                val partialByte = partialBlock % 8
                partialBlock -= partialByte

                val index = off + (i / 8).toInt()
                data.copyInto(
                    destination = dataQueue,
                    destinationOffset = bitsInQueue / 8,
                    startIndex = off + (i / 8).toInt(),
                    endIndex = index + partialBlock / 8,
                )

                bitsInQueue += partialBlock
                i += partialBlock.toLong()

                if (bitsInQueue == rate) {
                    absorbQueue()
                }

                if (partialByte > 0) {
                    val mask = (1 shl partialByte) - 1
                    dataQueue[bitsInQueue / 8] =
                        (data[off + ((i / 8).toInt())].toInt() and mask).toByte()
                    bitsInQueue += partialByte
                    i += partialByte.toLong()
                }
            }
        }
    }

    private fun padAndSwitchToSqueezingPhase() {
        if (bitsInQueue + 1 == rate) {
            dataQueue[bitsInQueue / 8] =
                (dataQueue[bitsInQueue / 8].toInt() or (1 shl (bitsInQueue % 8))).toByte()
            absorbQueue()
            clearDataQueueSection(0, rate / 8)
        } else {
            clearDataQueueSection((bitsInQueue + 7) / 8, rate / 8 - (bitsInQueue + 7) / 8)
            dataQueue[bitsInQueue / 8] =
                (dataQueue[bitsInQueue / 8].toInt() or (1 shl (bitsInQueue % 8))).toByte()
        }

        dataQueue[(rate - 1) / 8] = (dataQueue[(rate - 1) / 8].toInt() or (1 shl ((rate - 1) % 8))).toByte()
        absorbQueue()

        //            displayIntermediateValues.displayText(1, "--- Switching to squeezing phase ---");
        if (rate == 1024) {
            keccakExtract1024bits(state, dataQueue)
            bitsAvailableForSqueezing = 1024
        } else {
            keccakExtract(state, dataQueue, rate / 64)
            bitsAvailableForSqueezing = rate
        }

        //            displayIntermediateValues.displayBytes(1, "Block available for squeezing", dataQueue, bitsAvailableForSqueezing / 8);
        squeezing = true
    }

    fun squeeze(
        output: ByteArray,
        offset: Int,
        outputLength: Long,
    ) {
        var i: Long
        var partialBlock: Int

        if (!squeezing) {
            padAndSwitchToSqueezingPhase()
        }

        check((outputLength % 8) == 0L) { "outputLength not a multiple of 8" }

        i = 0

        while (i < outputLength) {
            if (bitsAvailableForSqueezing == 0) {
                keccakPermutation(state)

                if (rate == 1024) {
                    keccakExtract1024bits(state, dataQueue)
                    bitsAvailableForSqueezing = 1024
                } else {
                    keccakExtract(state, dataQueue, rate / 64)
                    bitsAvailableForSqueezing = rate
                }

                //                    displayIntermediateValues.displayBytes(1, "Block available for squeezing", dataQueue, bitsAvailableForSqueezing / 8);
            }

            partialBlock = bitsAvailableForSqueezing

            if (partialBlock.toLong() > outputLength - i) {
                partialBlock = (outputLength - i).toInt()
            }

            dataQueue.copyInto(
                output,
                offset + (i / 8).toInt(),
                (rate - bitsAvailableForSqueezing) / 8,
                (rate - bitsAvailableForSqueezing) / 8 + partialBlock / 8,
            )
            bitsAvailableForSqueezing -= partialBlock
            i += partialBlock.toLong()
        }
    }

    private fun fromBytesToWords(
        stateAsWords: LongArray,
        state: ByteArray,
    ) {
        for (i in 0..<(1600 / 64)) {
            stateAsWords[i] = 0
            val index = i * (64 / 8)

            for (j in 0..<(64 / 8)) {
                stateAsWords[i] =
                    stateAsWords[i] or ((state[index + j].toLong() and 0xffL) shl ((8 * j)))
            }
        }
    }

    private fun fromWordsToBytes(
        state: ByteArray,
        stateAsWords: LongArray,
    ) {
        for (i in 0..<(1600 / 64)) {
            val index = i * (64 / 8)

            for (j in 0..<(64 / 8)) {
                state[index + j] = ((stateAsWords[i] ushr ((8 * j))) and 0xFFL).toByte()
            }
        }
    }

    private fun keccakPermutation(state: ByteArray) {
        val longState = LongArray(state.size / 8)

        fromBytesToWords(longState, state)

        //        displayIntermediateValues.displayStateAsBytes(1, "Input of permutation", longState);
        keccakPermutationOnWords(longState)

        //        displayIntermediateValues.displayStateAsBytes(1, "State after permutation", longState);
        fromWordsToBytes(state, longState)
    }

    private fun keccakPermutationAfterXor(
        state: ByteArray,
        data: ByteArray,
        dataLengthInBytes: Int,
    ) {
        var i: Int

        i = 0
        while (i < dataLengthInBytes) {
            state[i] = (state[i].toInt() xor data[i].toInt()).toByte()
            i++
        }

        keccakPermutation(state)
    }

    private fun keccakPermutationOnWords(state: LongArray) {
        var i: Int

        //        displayIntermediateValues.displayStateAs64bitWords(3, "Same, with lanes as 64-bit words", state);
        i = 0
        while (i < 24) {
            //            displayIntermediateValues.displayRoundNumber(3, i);
            theta(state)

            //            displayIntermediateValues.displayStateAs64bitWords(3, "After theta", state);
            rho(state)

            //            displayIntermediateValues.displayStateAs64bitWords(3, "After rho", state);
            pi(state)

            //            displayIntermediateValues.displayStateAs64bitWords(3, "After pi", state);
            chi(state)

            //            displayIntermediateValues.displayStateAs64bitWords(3, "After chi", state);
            iota(state, i)
            i++
        }
    }

    var cArray: LongArray = LongArray(5)

    private fun theta(array: LongArray) {
        for (x in 0..4) {
            cArray[x] = 0

            for (y in 0..4) {
                cArray[x] = cArray[x] xor array[x + 5 * y]
            }
        }
        for (x in 0..4) {
            val dX =
                ((((cArray[(x + 1) % 5]) shl 1) xor ((cArray[(x + 1) % 5]) ushr (64 - 1)))) xor cArray[(x + 4) % 5]

            for (y in 0..4) {
                array[x + 5 * y] = array[x + 5 * y] xor dX
            }
        }
    }

    private fun rho(array: LongArray) {
        for (x in 0..4) {
            for (y in 0..4) {
                val index = x + 5 * y
                array[index] =
                    (if (KeccakRhoOffsets[index] != 0) (((array[index]) shl KeccakRhoOffsets[index]) xor ((array[index]) ushr (64 - KeccakRhoOffsets[index]))) else array[index])
            }
        }
    }

    var tempA: LongArray = LongArray(25)

    private fun pi(array: LongArray) {
        array.copyInto(tempA, 0, 0, tempA.size)

        for (x in 0..4) {
            for (y in 0..4) {
                array[y + 5 * ((2 * x + 3 * y) % 5)] = tempA[x + 5 * y]
            }
        }
    }

    var chiC: LongArray = LongArray(5)

    private fun chi(array: LongArray) {
        for (y in 0..4) {
            for (x in 0..4) {
                chiC[x] =
                    array[x + 5 * y] xor ((array[(((x + 1) % 5) + 5 * y)].inv()) and array[(((x + 2) % 5) + 5 * y)])
            }

            for (x in 0..4) {
                array[x + 5 * y] = chiC[x]
            }
        }
    }

    private fun iota(
        array: LongArray,
        indexRound: Int,
    ) {
        array[(((0) % 5) + 5 * ((0) % 5))] =
            array[(((0) % 5) + 5 * ((0) % 5))] xor KeccakRoundConstants[indexRound]
    }

    private fun keccakAbsorb(
        byteState: ByteArray,
        data: ByteArray,
        dataInBytes: Int,
    ) {
        keccakPermutationAfterXor(byteState, data, dataInBytes)
    }

    private fun keccakExtract1024bits(
        byteState: ByteArray,
        data: ByteArray,
    ) {
        byteState.copyInto(data, 0, 0, 128)
    }

    private fun keccakExtract(
        byteState: ByteArray,
        data: ByteArray,
        laneCount: Int,
    ) {
        byteState.copyInto(data, 0, 0, laneCount * 8)
    }

    companion object {
        private val KeccakRoundConstants: LongArray = keccakInitializeRoundConstants()

        private val KeccakRhoOffsets: IntArray = keccakInitializeRhoOffsets()

        private fun keccakInitializeRoundConstants(): LongArray {
            val keccakRoundConstants = LongArray(24)
            val lfsrState = ByteArray(1)

            lfsrState[0] = 0x01
            var i: Int
            var j: Int
            var bitPosition: Int

            i = 0
            while (i < 24) {
                keccakRoundConstants[i] = 0

                j = 0
                while (j < 7) {
                    bitPosition = (1 shl j) - 1

                    if (lfsr86540(lfsrState)) {
                        keccakRoundConstants[i] = keccakRoundConstants[i] xor (1L shl bitPosition)
                    }
                    j++
                }
                i++
            }

            return keccakRoundConstants
        }

        private fun lfsr86540(lfsr: ByteArray): Boolean {
            val result = (((lfsr[0]).toInt() and 0x01) != 0)

            if (((lfsr[0]).toInt() and 0x80) != 0) {
                lfsr[0] = (((lfsr[0]).toInt() shl 1) xor 0x71).toByte()
            } else {
                lfsr[0] = (lfsr[0].toInt() shl 1).toByte()
            }

            return result
        }

        private fun keccakInitializeRhoOffsets(): IntArray {
            val keccakRhoOffsets = IntArray(25)
            var x: Int
            var y: Int
            var t: Int
            var newX: Int
            var newY: Int

            keccakRhoOffsets[(((0) % 5) + 5 * ((0) % 5))] = 0
            x = 1
            y = 0

            t = 0
            while (t < 24) {
                keccakRhoOffsets[(((x) % 5) + 5 * ((y) % 5))] = ((t + 1) * (t + 2) / 2) % 64
                newX = (0 * x + 1 * y) % 5
                newY = (2 * x + 3 * y) % 5
                x = newX
                y = newY
                t++
            }

            return keccakRhoOffsets
        }
    }
}

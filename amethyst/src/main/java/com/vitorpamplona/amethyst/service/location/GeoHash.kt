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
package com.vitorpamplona.amethyst.service.location

import android.location.Location

/**
 * GeoHash encoding/decoding based on Gustavo Niemeyer's algorithm (2008).
 *
 * Bits are interleaved: even-indexed bits (0, 2, 4, ...) = longitude,
 * odd-indexed bits (1, 3, 5, ...) = latitude.
 */
class GeoHash private constructor(
    // Left-aligned: MSB is bit 0 of the hash
    private val bits: Long,
    private val significantBits: Int,
    val centerLat: Double,
    val centerLon: Double,
) {
    companion object {
        const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
        private const val BITS_PER_CHAR = 5
        const val MAX_CHAR_PRECISION = 12
        private const val MAX_BITS = 64
        private const val LAT_MAX = 90.0
        private const val LON_MAX = 180.0

        // ASCII → base32 index; -1 for invalid characters
        private val CHAR_INDEX =
            IntArray(128) { -1 }.also {
                BASE32.forEachIndexed { i, c -> it[c.code] = i }
            }

        /** Encode [lat]/[lon] into a GeoHash with [charsCount] character precision. */
        fun encode(
            lat: Double,
            lon: Double,
            charsCount: Int,
        ): GeoHash {
            require(charsCount in 1..MAX_CHAR_PRECISION)
            val targetBits = charsCount * BITS_PER_CHAR
            val latRange = doubleArrayOf(-LAT_MAX, LAT_MAX)
            val lonRange = doubleArrayOf(-LON_MAX, LON_MAX)
            var bits = 0L
            var isEven = true

            repeat(targetBits) {
                if (isEven) {
                    val mid = (lonRange[0] + lonRange[1]) * 0.5
                    if (lon >= mid) {
                        bits = (bits shl 1) or 1L
                        lonRange[0] = mid
                    } else {
                        bits = bits shl 1
                        lonRange[1] = mid
                    }
                } else {
                    val mid = (latRange[0] + latRange[1]) * 0.5
                    if (lat >= mid) {
                        bits = (bits shl 1) or 1L
                        latRange[0] = mid
                    } else {
                        bits = bits shl 1
                        latRange[1] = mid
                    }
                }
                isEven = !isEven
            }

            return GeoHash(
                bits = bits shl (MAX_BITS - targetBits),
                significantBits = targetBits,
                centerLat = (latRange[0] + latRange[1]) * 0.5,
                centerLon = (lonRange[0] + lonRange[1]) * 0.5,
            )
        }

        /** Decode a GeoHash string. Returns null if the string is invalid. */
        fun decode(hash: String): GeoHash? {
            if (hash.isEmpty() || hash.length > MAX_CHAR_PRECISION) return null
            val latRange = doubleArrayOf(-LAT_MAX, LAT_MAX)
            val lonRange = doubleArrayOf(-LON_MAX, LON_MAX)
            var bits = 0L
            var isEven = true

            for (c in hash) {
                val code = c.code
                if (code >= 128) return null
                val index = CHAR_INDEX[code]
                if (index < 0) return null

                for (j in 4 downTo 0) {
                    val bitSet = (index ushr j) and 1 == 1
                    if (isEven) {
                        val mid = (lonRange[0] + lonRange[1]) * 0.5
                        if (bitSet) {
                            bits = (bits shl 1) or 1L
                            lonRange[0] = mid
                        } else {
                            bits = bits shl 1
                            lonRange[1] = mid
                        }
                    } else {
                        val mid = (latRange[0] + latRange[1]) * 0.5
                        if (bitSet) {
                            bits = (bits shl 1) or 1L
                            latRange[0] = mid
                        } else {
                            bits = bits shl 1
                            latRange[1] = mid
                        }
                    }
                    isEven = !isEven
                }
            }

            val sigBits = hash.length * BITS_PER_CHAR
            return GeoHash(
                bits = bits shl (MAX_BITS - sigBits),
                significantBits = sigBits,
                centerLat = (latRange[0] + latRange[1]) * 0.5,
                centerLon = (lonRange[0] + lonRange[1]) * 0.5,
            )
        }

        /**
         * Reconstruct a GeoHash by re-interleaving separated lat/lon bit arrays.
         * Used internally to compute cardinal neighbors.
         */
        private fun fromBitArrays(
            latBits: Long,
            numLat: Int,
            lonBits: Long,
            numLon: Int,
        ): GeoHash {
            // Left-align both arrays
            var lb = latBits shl (MAX_BITS - numLat)
            var lob = lonBits shl (MAX_BITS - numLon)
            val total = numLat + numLon

            val latRange = doubleArrayOf(-LAT_MAX, LAT_MAX)
            val lonRange = doubleArrayOf(-LON_MAX, LON_MAX)
            var newBits = 0L
            // isEven=false → lon first (even-indexed positions = lon in GeoHash)
            var isEven = false

            repeat(total) {
                if (isEven) {
                    val bitSet = lb < 0L
                    lb = lb shl 1
                    val mid = (latRange[0] + latRange[1]) * 0.5
                    if (bitSet) {
                        newBits = (newBits shl 1) or 1L
                        latRange[0] = mid
                    } else {
                        newBits = newBits shl 1
                        latRange[1] = mid
                    }
                } else {
                    val bitSet = lob < 0L
                    lob = lob shl 1
                    val mid = (lonRange[0] + lonRange[1]) * 0.5
                    if (bitSet) {
                        newBits = (newBits shl 1) or 1L
                        lonRange[0] = mid
                    } else {
                        newBits = newBits shl 1
                        lonRange[1] = mid
                    }
                }
                isEven = !isEven
            }

            return GeoHash(
                newBits shl (MAX_BITS - total),
                total,
                (latRange[0] + latRange[1]) * 0.5,
                (lonRange[0] + lonRange[1]) * 0.5,
            )
        }
    }

    // Longitude gets the extra bit when significantBits is odd (lon is encoded first)
    private val numLat = significantBits / 2
    private val numLon = significantBits / 2 + significantBits % 2

    /**
     * Extracts every other bit from [source] (positions 0, 2, 4, …) into a right-aligned value.
     * Used to separate the interleaved lat/lon bit streams.
     */
    private fun extractEvery2ndBit(
        source: Long,
        count: Int,
    ): Long {
        var src = source
        var result = 0L
        repeat(count) {
            result = result shl 1
            if (src < 0L) result = result or 1L
            src = src shl 2
        }
        return result
    }

    /** Keep only the lowest [n] bits of [value], wrapping neighbors at poles/antimeridian. */
    private fun mask(
        value: Long,
        n: Int,
    ): Long = value and (-1L ushr (MAX_BITS - n))

    // Lat bits occupy odd positions (1, 3, 5, …); shift left 1 to align them to even positions.
    private val latBits get() = extractEvery2ndBit(bits shl 1, numLat)

    // Lon bits already occupy even positions (0, 2, 4, …).
    private val lonBits get() = extractEvery2ndBit(bits, numLon)

    val northernNeighbour: GeoHash get() = fromBitArrays(mask(latBits + 1L, numLat), numLat, lonBits, numLon)
    val southernNeighbour: GeoHash get() = fromBitArrays(mask(latBits - 1L, numLat), numLat, lonBits, numLon)
    val easternNeighbour: GeoHash get() = fromBitArrays(latBits, numLat, mask(lonBits + 1L, numLon), numLon)
    val westernNeighbour: GeoHash get() = fromBitArrays(latBits, numLat, mask(lonBits - 1L, numLon), numLon)

    fun toLocation(): Location =
        Location("").also {
            it.latitude = centerLat
            it.longitude = centerLon
        }

    override fun toString(): String {
        val charCount = significantBits / BITS_PER_CHAR
        val sb = StringBuilder(charCount)
        var b = bits
        repeat(charCount) {
            sb.append(BASE32[(b ushr 59).toInt() and 0x1f])
            b = b shl BITS_PER_CHAR
        }
        return sb.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GeoHash) return false
        return bits == other.bits && significantBits == other.significantBits
    }

    override fun hashCode(): Int = 31 * bits.hashCode() + significantBits
}

fun Location.toGeoHash(charsCount: Int = GeoHash.MAX_CHAR_PRECISION): GeoHash = GeoHash.encode(latitude, longitude, charsCount)

fun String.toGeoHash(): GeoHash? = GeoHash.decode(this)

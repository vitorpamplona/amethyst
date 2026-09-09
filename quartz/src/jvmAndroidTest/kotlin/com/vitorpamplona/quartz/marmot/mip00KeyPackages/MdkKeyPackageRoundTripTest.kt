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
package com.vitorpamplona.quartz.marmot.mip00KeyPackages

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * A KeyPackage we did not author has to survive decode -> re-encode byte for
 * byte, because the KeyPackageRef that names the joining member in a Welcome is
 * `RefHash("MLS 1.0 KeyPackage Reference", serialize(KeyPackage))` over exactly
 * those bytes. The invitee looks its private bundle up by that hash. One byte
 * of drift anywhere in the struct -- a dropped extension, a reordered
 * dictionary entry, a differently-sized length prefix -- and the Welcome names
 * a KeyPackage nobody has, so the invitee silently has nothing to join with.
 *
 * The fixture is a real KeyPackage published by MDK 0.9.20 (`wn`), captured off
 * the interop harness relay.
 */
@OptIn(ExperimentalEncodingApi::class)
class MdkKeyPackageRoundTripTest {
    private val mdkFramedKeyPackage = "AAEABQABAAEgGhmBTAXpFdSxyMphrHiFKeFO4cmy5AvBVb1NSULTC18gWC4G7Zsb/wLG17JOfUoWuNLG7MpbwUNz/pgGxFy9EUsgiXlykzgtwdj1WFEW8/pwDNMzuah4ukUFMiySi4Au1loAASCNeT1vOihOZ/jZIF1Lltoa/AY1+fTYX3P/uxDoiykUeQIAAQIAAQgABvLR8tLy1AQACgAIAgABAQAAAABqoLL8AAAAAGsPfwxAkgAGQI5AjAABGRgAAYABgAKAA4AEgAWABoAHgAiACYALgAwAAgEAgAlAaI15PW86KE5n+NkgXUuW2hr8BjX59Nhfc/+7EOiLKRR5AAAAAGqgwQxu5AAeyJtPRpsTzslSt1o2N+kwYHQG5wUPsGgedSFMdRqP4mZeNEn8hi5zImQORBhf7tf4hISvja/gd/prx4ecQEC1dvSgT+hvumSJOgjdmLt9Yq9YCUY1Ih45QZXAy3EzPWTRfr5URwifcj4sscM7k9g7MPoAJQ1P4apLTlSgt7ILBwAGBAMABABAQJUP1427jN2rhINxSQuF2pKugrrvb6Qzo7kUYCGL8cHNcf2QArnyBdPxgNX/IRYk/d0bdQcByU81gSGPeA42PA8="

    @Test
    fun reEncodesAnMdkKeyPackageByteForByte() {
        val framed = Base64.decode(mdkFramedKeyPackage)
        val keyPackage = KeyPackageUtils.decodeKeyPackage(framed)

        // The framed publication is 4 bytes of MLSMessage header plus the
        // KeyPackage struct; compare against the payload, not the envelope.
        val payload = framed.copyOfRange(4, framed.size)
        assertContentEquals(payload, keyPackage.toTlsBytes())
    }

    @Test
    fun reFramesToTheExactBytesMdkPublished() {
        val framed = Base64.decode(mdkFramedKeyPackage)
        val keyPackage = KeyPackageUtils.decodeKeyPackage(framed)
        assertContentEquals(framed, KeyPackageUtils.frameKeyPackage(keyPackage))
    }

    /**
     * The `i` tag MDK put on the publication is the KeyPackageRef MDK filed its
     * own private bundle under. Recomputing it from the bytes we decoded is the
     * end-to-end check: if these agree, a Welcome we address to this member
     * names a KeyPackage the member can actually find.
     */
    @Test
    fun computesTheSameReferenceMdkAdvertised() {
        val framed = Base64.decode(mdkFramedKeyPackage)
        val keyPackage = KeyPackageUtils.decodeKeyPackage(framed)
        assertEquals(MDK_ADVERTISED_REF, keyPackage.reference().toHexKey())
    }

    companion object {
        private const val MDK_ADVERTISED_REF = "1e6606f8e154a0c148587c4334f801fbb7aaf5a806d8971a87961a58366844ef"
    }
}

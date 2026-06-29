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
package com.vitorpamplona.quartz.nip34Git.git

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.Base64

class GitPackfileDeltaTest {
    // A real pack of two big.txt versions produced with:
    //   git pack-objects --stdout --delta-base-offset --window=10 --depth=10
    // It contains one full blob and one OBJ_OFS_DELTA against it.
    private val deltaPack =
        "UEFDSwAAAAIAAAACt4sDeJxl2LGOHFUURdHcX9Gf0Peequ6ukMBIlpADQCK2wAGSPQ5A4vdBZGc5fE68Zc+cVe/9cPv4/rfbjx9+/uXX208fPr5/9/u3r1+/vd2+/Pn2+Xav09Rp65Q6HXU66/So07NOrzpd/bcT0zXTOdM900HTRdNJ003TUdNV21XLv1FXbVdtV21XbVdtV21XbVelq9JV4b+uq9JV6ap0VboqXZWuOrrq6Kqjqw5+orrq6Kqjq46uOrrq6Kqzq86uOrvq7KqTH/SuOrvq7Kqzq86uenTVo6seXfXoqkdXPfj966pHVz266tFVz656dtWzq55d9eyqZ1c9mYWuenbVs6teXfXqqldXvbrq1VWvrnp11Yu16qpXV11ddXXV1VVXV11ddXXV1VVXV12MqCvKjN7Z0TtDemdJ70zpnS29M6Z31vTOnN7p+27m6XPoXXqn3q137F175569HwZ/VofoY/OH0R9Wf5j9YfeH4R+Wf5j+YfsnQkkf8z/s/wDAIMBAwGDAgMCgwMDAHEpOHxIMFAwWDBgMGgwcDB4MIAwizOmnBn2gMKgwsDC4MMAwyDDQMNgw4DAPv4Xow4cBiEGIgYjBiAGJQYmBicGJefqxRh9UDFYMWAxaDFwMXgxgDGIMZMzLr0n6UGNgY3BjgGOQY6BjsGPAY9BjLj93/d7lgxc/Fj8WPxY/Fj8WPxY/Fj8WP3b8IKcPPxY/Fj8WPxY/Fj8WP9b7gheG724M9Hln8NLgrcFrg/cGLw74sfix+LHxSkMffix+LH4sfix+LH4sfix+LH7s4Z2LPvxY/Fj8WPxY/Fj8WPxY/Fj82NNLIX34sfix+LH4sfix+LH4sfix+LEPb6304cfix+LH4sfix+LH4sfix+LHPr1W04cfix+LH4sfix+LH4sfix+LH/vy3k8ffix+LH4sfix+LH4sfix+LH7s5cOELxM8TeBH8CP4EfwIfgQ/gh/Bj+BHxqcT+vAj+BH8CH4EP4IfwY/gR/Aj69sOffgR/Ah+BD+CH8GP+PLk05NvT989PtHn85PvTz5A+QLlExR+BD+CH8GPHL6O0YcfwY/gR/Aj+BH8CH4EP4IfOX2+ow8/gh/Bj+BH8CP4EfwIfgQ/gh/Bj+BH8CP4EfwIfgQ/gh/Bj+BH8CP4EfwIfgQ/gh/Bj+BH8CP4EfwIfgQ/gh/Bj+BH8CP4EfwIfgQ/gh/Bj/znx6e3P26fbm+f/7l9+fTX3///+bt/ARJv6GvuAYYkeJzbbrjCUDQnMy9VIT9NoSS1okQhrzQ3KbVIwWCjwGQJAKvrCr5mknNvxZN0+vEf6iRSfsYDsdzxuw=="

    private fun bigTxtCommon() = (0..399).joinToString("") { "common line $it\n" }

    @Test
    fun resolvesOfsDeltaAndComputesOids() {
        val pack = Base64.getDecoder().decode(deltaPack)
        val objects = Packfile.parse(pack)

        val common = bigTxtCommon()
        val expectedHead = "A NEW FIRST LINE\n" + common + "and a new last line\n"
        val expectedPrev = "line of text number 0\n" + common

        val head = objects["e873ccdfb69d504f8ce2d51255da3c530d191f21"]
        assertNotNull("full base blob present", head)
        assertEquals(GitObjectType.BLOB, head!!.type)
        assertEquals(expectedHead, head.data.decodeToString())

        val prev = objects["eb06ed9c0291ae7fd14aec388d470a1bff363f1e"]
        assertNotNull("delta blob resolved", prev)
        assertEquals(GitObjectType.BLOB, prev!!.type)
        // The oid is recomputed from the reconstructed bytes, so a correct oid key
        // proves both the delta application and the SHA-1 hashing are right.
        assertEquals(expectedPrev, prev.data.decodeToString())
    }

    @Test
    fun appliesHandcraftedDelta() {
        val base = "the quick brown fox".encodeToByteArray()
        // delta: copy "the quick " (offset 0, size 10), insert "red ", copy "fox" (offset 16, size 3)
        val delta =
            byteArrayOf(
                base.size.toByte(), // base size varint (19)
                ("the quick ".length + "red ".length + "fox".length).toByte(), // target size (17)
                // copy op: cmd 0x90 = copy, offset 0 (no offset bytes), size from one byte (0x0A = 10)
                0x90.toByte(),
                0x0A,
                // insert op: literal of 4 bytes
                0x04,
                'r'.code.toByte(),
                'e'.code.toByte(),
                'd'.code.toByte(),
                ' '.code.toByte(),
                // copy op: cmd 0x91 = copy, offset from one byte (0x10 = 16), size from one byte (0x03)
                0x91.toByte(),
                0x10,
                0x03,
            )
        val result = GitDelta.apply(base, delta)
        assertEquals("the quick red fox", result.decodeToString())
    }

    @Test
    fun parsesTreeEntries() {
        // Build a tree payload: "100644 README\0<20 bytes>" + "40000 src\0<20 bytes>"
        val out = java.io.ByteArrayOutputStream()
        out.write("100644 README".encodeToByteArray())
        out.write(0)
        out.write(hex("980a0d5f19a64b4b30a87d4206aade58726b60e3"))
        out.write("40000 src".encodeToByteArray())
        out.write(0)
        out.write(hex("b4eecafa9be2f2006ce1b709d6857b07069b4608"))
        val entries = GitObjectParser.parseTree(out.toByteArray())

        assertEquals(2, entries.size)
        assertEquals("README", entries[0].name)
        assertEquals("980a0d5f19a64b4b30a87d4206aade58726b60e3", entries[0].oid)
        assertEquals(false, entries[0].isFolder)
        assertEquals("src", entries[1].name)
        assertEquals(true, entries[1].isFolder)
    }

    @Test
    fun parsesCommitTreeHeader() {
        val commit =
            (
                "tree b4eecafa9be2f2006ce1b709d6857b07069b4608\n" +
                    "parent 0000000000000000000000000000000000000000\n" +
                    "author Someone <a@b.c> 1 +0000\n\nmessage\n"
            ).encodeToByteArray()
        assertEquals("b4eecafa9be2f2006ce1b709d6857b07069b4608", GitObjectParser.parseCommitTree(commit))
    }

    @Test
    fun roundTripsPktLine() {
        val frame = PktLineCodec.dataLine("want abc\n")
        assertArrayEquals("000dwant abc\n".encodeToByteArray(), frame)
        val parsed = PktLineCodec.parse(frame + PktLineCodec.FLUSH + PktLineCodec.DELIM)
        assertEquals(3, parsed.size)
        assertEquals("want abc", (parsed[0] as PktLine.Data).text())
        assertEquals(PktLine.Flush, parsed[1])
        assertEquals(PktLine.Delim, parsed[2])
    }

    private fun hex(s: String): ByteArray = ByteArray(s.length / 2) { ((s[it * 2].digitToInt(16) shl 4) or s[it * 2 + 1].digitToInt(16)).toByte() }
}

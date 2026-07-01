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

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

class GitCommitParserTest {
    // A real, SSH-signed commit object (raw `git cat-file commit HEAD`), so the
    // multi-line `gpgsig` header must be skipped without breaking parsing.
    private val signedCommitB64 =
        "dHJlZSA5YjZiM2Y5YmEzYjQxMjUxZTQzZGY0MDJiMjM0Njc4NzBiNDA4YTU4CnBhcmVudCBlNTc2ZjVmY2FmZjBkMDAwNGI2NmIzMjE2YmI2MTlhN2UyNjIwMTA2CmF1dGhvciBKYW5lIERldiA8YUBiLmNvbT4gMTc4MjY4MTU3NSArMDAwMApjb21taXR0ZXIgSmFuZSBEZXYgPGFAYi5jb20+IDE3ODI2ODE1NzUgKzAwMDAKZ3Bnc2lnIC0tLS0tQkVHSU4gU1NIIFNJR05BVFVSRS0tLS0tCiBVMU5JVTBsSEFBQUFBUUFBQURNQUFBQUxjM05vTFdWa01qVTFNVGtBQUFBZ3JMenNmRklTRjRieThRK0ZLejI3WXBrSzFVU3NCQittCiBhbXUxUWtKbmJEc0FBQUFEWjJsMEFBQUFBQUFBQUFaemFHRTFNVElBQUFCVEFBQUFDM056YUMxbFpESTFOVEU1QUFBQVFNRVUrNE1HCiAxU0tyWmVIUE5zeldQRGk1TzRIN3IyeU1mMkpWeGx1SG5xV2V4WTBHWHN1ZXBrSTBETXBOMldmWHZvMDc3cm9ac2Ivejhnb3RJSS92CiBCd009CiAtLS0tLUVORCBTU0ggU0lHTkFUVVJFLS0tLS0KCnNlY29uZCBjb21taXQKCndpdGggYSBib2R5IGxpbmUK"

    @Test
    fun parsesSignedCommit() {
        val data = Base64.getDecoder().decode(signedCommitB64)
        val commit = GitObjectParser.parseCommit("20f90551a0e493306229a6b50990d3025b721cb0", data)

        assertEquals("9b6b3f9ba3b41251e43df402b23467870b408a58", commit.treeOid)
        assertEquals(listOf("e576f5fcaff0d0004b66b3216bb619a7e2620106"), commit.parents)
        assertEquals("Jane Dev", commit.authorName)
        assertEquals("a@b.com", commit.authorEmail)
        assertEquals(1782681575L, commit.authorTimeSec)
        assertEquals("second commit", commit.summary)
        assertEquals("20f9055", commit.shortOid)
        // The signature block must not leak into the message.
        assertEquals("second commit\n\nwith a body line\n", commit.message)
    }
}

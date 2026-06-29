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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Verifies the protocol-v2 parsers against real bytes captured from
 * `github.com/octocat/Hello-World.git`. These fixtures are static so the test
 * runs offline.
 */
class GitProtocolV2Test {
    private fun decode(b64: String): ByteArray = Base64.getDecoder().decode(b64)

    // GET /info/refs?service=git-upload-pack with Git-Protocol: version=2
    private val infoRefs =
        "MDAxZSMgc2VydmljZT1naXQtdXBsb2FkLXBhY2sKMDAwMDAwMGV2ZXJzaW9uIDIKMDAyOGFnZW50PWdpdC9naXRodWItZTBiNThhZGU0OTgwLUxpbnV4CjAwMTNscy1yZWZzPXVuYm9ybgowMDI3ZmV0Y2g9c2hhbGxvdyB3YWl0LWZvci1kb25lIGZpbHRlcgowMDEyc2VydmVyLW9wdGlvbgowMDE3b2JqZWN0LWZvcm1hdD1zaGExCjAwMDA="

    // POST git-upload-pack command=ls-refs response
    private val lsRefs =
        "MDA1MjdmZDFhNjBiMDFmOTFiMzE0ZjU5OTU1YTRlNGQ0ZTgwZDhlZGYxMWQgSEVBRCBzeW1yZWYtdGFyZ2V0OnJlZnMvaGVhZHMvbWFzdGVyCjAwM2Y3ZmQxYTYwYjAxZjkxYjMxNGY1OTk1NWE0ZTRkNGU4MGQ4ZWRmMTFkIHJlZnMvaGVhZHMvbWFzdGVyCjAwNDhiMWIzZjk3MjM4MzExNDFhMzFhMWE3MjUyYTIxM2UyMTZlYTc2ZTU2IHJlZnMvaGVhZHMvb2N0b2NhdC1wYXRjaC0xCjAwM2RiM2NiZDViYmQ3ZTgxNDM2ZDJlZWUwNDUzN2VhMmI0YzBjYWQ0Y2RmIHJlZnMvaGVhZHMvdGVzdAowMDAw"

    // POST git-upload-pack command=fetch (want HEAD, deepen 1, filter blob:none)
    private val fetchResp =
        "MDAxMXNoYWxsb3ctaW5mbwowMDM0c2hhbGxvdyA3ZmQxYTYwYjAxZjkxYjMxNGY1OTk1NWE0ZTRkNGU4MGQ4ZWRmMTFkMDAwMTAwMGRwYWNrZmlsZQowMTMwAVBBQ0sAAAACAAAAAp0UeJydjUtqwzAURedexYOOkz79LQilG2g7aDegz1VssC1XUcj2ayjdQGeXwz2c3gCKGkihBB8hi2S2CSI69tmOxkV2bH3UlsdhDw1bJ2NUkuxcYeSkssnJZOGklcWGoP0Ie6hlxN/fWem1UGIEhIXxOcQYUhRRs/ZHWsrCWbEawr1PtdHXBPpIvabQ6VJ/x+tWHxMazqmuLySUEuyMFEwnHpmHg65z7/iXPLyhXUH7fVmo4fuOW6cnS6XVlT73kHCd6q0/76Gn6SSG4R0PWuYNdCSwZaqFyrzg/ANUZmZ/ogJ4nDM0MDAzMVEIcnV08XVlmMHFGy+5zNvbYEWtE9uqexFF2QmPAZo4Cv5JlwtXPkO1ygyeE+5XX4ICt6XpMDAwNgF9MDAwMA=="

    // POST git-upload-pack command=fetch (want the README blob by oid)
    private val blobResp =
        "MDAwZHBhY2tmaWxlCjAwM2EBUEFDSwAAAAIAAAABPXic80jNyclXCM8vyklR5AIAIJEESLzcQ5zr061K/FSjSFUFPLVK/b0wMDA2AQgwMDAw"

    @Test
    fun parsesCapabilities() {
        val caps = GitUploadPackV2.parseCapabilities(PktLineCodec.parse(decode(infoRefs)))
        assertEquals("sha1", caps.objectFormat)
        assertTrue(caps.supportsLsRefs)
        assertTrue(caps.supportsFetch)
        assertTrue("server advertised filter", caps.supportsFilter)
        assertTrue("server advertised shallow", caps.supportsShallow)
        assertEquals("git/github-e0b58ade4980-Linux", caps.agent)
    }

    @Test
    fun parsesLsRefs() {
        val refs = GitUploadPackV2.parseRefs(PktLineCodec.parse(decode(lsRefs)))
        val head = refs.first { it.name == "HEAD" }
        assertEquals("7fd1a60b01f91b314f59955a4e4d4e80d8edf11d", head.oid)
        assertEquals("refs/heads/master", head.symrefTarget)
        assertNotNull(refs.firstOrNull { it.name == "refs/heads/master" })
        assertNotNull(refs.firstOrNull { it.name == "refs/heads/test" })
    }

    @Test
    fun demuxesAndParsesShallowPack() {
        val pack = GitUploadPackV2.extractPack(PktLineCodec.parse(decode(fetchResp)))
        val objects = Packfile.parse(pack)

        // commit + root tree, blobs filtered out
        val commit = objects["7fd1a60b01f91b314f59955a4e4d4e80d8edf11d"]
        assertNotNull(commit)
        assertEquals(GitObjectType.COMMIT, commit!!.type)

        val rootTreeOid = GitObjectParser.parseCommitTree(commit.data)
        assertEquals("b4eecafa9be2f2006ce1b709d6857b07069b4608", rootTreeOid)

        val tree = objects[rootTreeOid]!!
        assertEquals(GitObjectType.TREE, tree.type)
        val entries = GitObjectParser.parseTree(tree.data)
        val readme = entries.first { it.name == "README" }
        assertEquals("980a0d5f19a64b4b30a87d4206aade58726b60e3", readme.oid)
        assertFalse(readme.isFolder)

        // blob:none means the README content is not in this pack
        assertFalse(objects.containsKey(readme.oid))
    }

    @Test
    fun parsesSingleBlobFetch() {
        val pack = GitUploadPackV2.extractPack(PktLineCodec.parse(decode(blobResp)))
        val objects = Packfile.parse(pack)
        val blob = objects["980a0d5f19a64b4b30a87d4206aade58726b60e3"]
        assertNotNull(blob)
        assertEquals(GitObjectType.BLOB, blob!!.type)
        assertEquals("Hello World!\n", blob.data.decodeToString())
    }
}

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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.quartz.cordn.appGroupRef.CordnGroupRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The parse-and-disclose step behind the "someone sent me a cordn link" screen.
 *
 * Headless on purpose: the screen draws whatever this returns, so the rules
 * that matter — what counts as a link, what a link without a coordinator means,
 * and what §8 says about joining through one — are checkable without a device.
 */
class CordnLinkInspectionTest {
    private val gid = "6d1f0f6a-2a3e-4f2c-9a1d-7c6b5e4d3a21"
    private val coordinator = "cc".repeat(32)

    private fun link(
        withCoordinator: Boolean = true,
        relays: List<String> = listOf("wss://relay.example.com/"),
    ) = CordnGroupRef(
        gid = gid,
        coordinatorPubKey = if (withCoordinator) coordinator else null,
        relays = if (withCoordinator) relays else emptyList(),
    ).encode()

    @Test
    fun `a full link resolves to a coordinator and a disclosure`() {
        val result = CordnLinkInspection.of(link())
        assertTrue(result is CordnLinkInspection.Valid, "got $result")
        assertEquals(gid, result.ref.gid)
        assertTrue(result.isFollowable)

        val config = assertNotNull(result.coordinator)
        assertEquals(coordinator, config.pubKey)
        assertEquals(CoordinatorConfig.Origin.GROUP_REF, config.origin, "a pasted link is not a coordinator the user chose")

        val exposure = assertNotNull(result.exposure)
        assertEquals(ExposureLevel.NONE, exposure.content)
        assertEquals(ExposureLevel.IDENTIFIED, exposure.membership)
        assertTrue(exposure.joinedFromShareLink, "joining by link is the §8.1 path that names you")
    }

    @Test
    fun `a link naming no coordinator is valid but not followable`() {
        // spec/applications/group-ref.md §2 makes the coordinator optional. The
        // ref still identifies a group; it just does not say who serves it, and
        // there is nobody to disclose anything about.
        val result = CordnLinkInspection.of(link(withCoordinator = false))
        assertTrue(result is CordnLinkInspection.Valid)
        assertEquals(gid, result.ref.gid)
        assertTrue(!result.isFollowable)
        assertNull(result.coordinator)
        assertNull(result.exposure, "inventing a threat model for an unnamed server would be worse than saying nothing")
    }

    @Test
    fun `the linkage count describes what joining would create`() {
        // §8.2: one throwaway key covers every group on a coordinator, so the
        // warning is only true once there is a second group to link to. The
        // count has to include the group being joined.
        val alone = CordnLinkInspection.of(link()) as CordnLinkInspection.Valid
        assertEquals(1, alone.exposure!!.linkedGroupCount)
        assertTrue(ExposureNote.GROUPS_LINKED_BY_SESSION !in alone.exposure.notes())

        val joining = CordnLinkInspection.of(link(), existingGroupsOnCoordinator = 2) as CordnLinkInspection.Valid
        assertEquals(3, joining.exposure!!.linkedGroupCount)
        assertTrue(ExposureNote.GROUPS_LINKED_BY_SESSION in joining.exposure.notes())
    }

    @Test
    fun `a published key package is disclosed, because the coordinator can re-serve it`() {
        val quiet = CordnLinkInspection.of(link()) as CordnLinkInspection.Valid
        assertTrue(ExposureNote.PUBLICATION_IS_A_SIGNED_RECORD !in quiet.exposure!!.notes())

        val published = CordnLinkInspection.of(link(), publishedKeyPackage = true) as CordnLinkInspection.Valid
        assertTrue(ExposureNote.PUBLICATION_IS_A_SIGNED_RECORD in published.exposure!!.notes(), "§8.4")
    }

    @Test
    fun `pasted whitespace and uppercase still resolve`() {
        // What actually arrives from a clipboard: a trailing newline from a
        // chat app, or a ref someone typed in caps. Bech32 permits the
        // uppercase form and cordn's own decoder accepts it.
        assertTrue(CordnLinkInspection.of("  ${link()}\n") is CordnLinkInspection.Valid)
        assertTrue(CordnLinkInspection.of(link().uppercase()) is CordnLinkInspection.Valid)
    }

    @Test
    fun `a cordn net share link resolves to the ref inside it`() {
        // What cordn.net's "Share group link" copies and puts in its QR code:
        // the ref as a /chat/ path, plus `m`, a base64url JSON name/icon
        // preview that carries nothing needed to reach the group.
        val preview = "eyJuYW1lIjoiVGVzdCBncm91cCIsImljb24iOiLwn5KsIn0"
        listOf(
            "https://cordn.net/chat/${link()}?m=$preview",
            "https://cordn.net/chat/${link()}",
            "  https://cordn.net/chat/${link()}?m=$preview\n",
            "HTTPS://CORDN.NET/chat/${link()}",
            "cordn.net/chat/${link()}",
            "https://www.cordn.net/chat/${link()}#top",
            // The group info page and message permalinks hang off the same ref.
            "https://cordn.net/chat/${link()}/info",
            // A self-hosted instance of the same client builds the same path.
            "https://chat.example.org/chat/${link()}?m=$preview",
        ).forEach {
            val result = CordnLinkInspection.of(it)
            assertTrue(result is CordnLinkInspection.Valid, "must accept '$it', got $result")
            assertEquals(gid, result.ref.gid)
            assertEquals(coordinator, result.coordinator?.pubKey)
            assertEquals(listOf("wss://relay.example.com/"), result.coordinator?.relays?.map { it.url })
        }
    }

    @Test
    fun `a share link with a broken ref is refused with the decoder's reason`() {
        val broken = link().dropLast(1) + if (link().last() == 'q') "p" else "q"
        val result = CordnLinkInspection.of("https://cordn.net/chat/$broken?m=e30")
        assertTrue(result is CordnLinkInspection.Invalid, "got $result")
        assertTrue(result.reason.isNotEmpty())
    }

    @Test
    fun `everything else is refused, with the reason the decoder gave`() {
        listOf(
            "",
            "   ",
            "nostr1qqqqq",
            "cordn1qqqqq",
            "npub1xxxx",
            "https://cordn.net/g/abc",
            // Not a group: cordn.net's other pages, and its pre-cordn1 links
            // that named a bare gid and left the coordinator to a `c` param.
            "https://cordn.net/chat",
            "https://cordn.net/chat/news",
            "https://cordn.net/p/npub1xxxx",
            "https://cordn.net/chat/6d1f0f6a-2a3e-4f2c?c=npub1xxxx",
            "https://cordn.net/other/${link()}",
        ).forEach {
            val result = CordnLinkInspection.of(it)
            assertTrue(result is CordnLinkInspection.Invalid, "must refuse '$it', got $result")
            assertTrue(result.reason.isNotEmpty(), "a refusal with no reason is a dead end for the user")
        }
    }
}

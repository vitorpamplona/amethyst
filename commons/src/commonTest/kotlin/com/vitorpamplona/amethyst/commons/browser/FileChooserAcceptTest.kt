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
package com.vitorpamplona.amethyst.commons.browser

import kotlin.test.Test
import kotlin.test.assertEquals

class FileChooserAcceptTest {
    /** Stands in for android.webkit.MimeTypeMap; only the extensions the tests use are known. */
    private val map =
        mapOf(
            "png" to "image/png",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "pdf" to "application/pdf",
            "mp4" to "video/mp4",
        )

    private fun resolve(vararg accept: String) = FileChooserAccept.resolve(accept.toList(), map::get)

    @Test
    fun noAcceptMeansEverything() {
        val resolved = resolve()
        assertEquals(FileChooserAccept.ANY, resolved.primaryType)
        assertEquals(emptyList(), resolved.mimeTypes)
    }

    @Test
    fun blankEntriesAreIgnored() {
        val resolved = resolve("", "   ")
        assertEquals(FileChooserAccept.ANY, resolved.primaryType)
        assertEquals(emptyList(), resolved.mimeTypes)
    }

    @Test
    fun singleMimeTypeIsUsedVerbatim() {
        val resolved = resolve("image/png")
        assertEquals("image/png", resolved.primaryType)
        assertEquals(listOf("image/png"), resolved.mimeTypes)
    }

    @Test
    fun wildcardIsKept() {
        val resolved = resolve("image/*")
        assertEquals("image/*", resolved.primaryType)
        assertEquals(listOf("image/*"), resolved.mimeTypes)
    }

    @Test
    fun extensionsResolveToMimeTypes() {
        val resolved = resolve(".png", ".pdf")
        assertEquals(listOf("image/png", "application/pdf"), resolved.mimeTypes)
    }

    @Test
    fun unknownExtensionShowsEverything() {
        // Narrowing the picker to a type the platform can't name would hide every file the user has.
        val resolved = resolve(".sqlite3")
        assertEquals(FileChooserAccept.ANY, resolved.primaryType)
        assertEquals(emptyList(), resolved.mimeTypes)
    }

    @Test
    fun oneUnknownExtensionWidensTheWholeFilter() {
        // MimeTypeMap is a fixed table and misses extensions pages do use. Filtering to just the half we
        // could name would leave the user staring at a picker with the wanted file missing, and `accept`
        // is only a hint in HTML — so an unnameable entry means show everything.
        val resolved = resolve(".png", ".sqlite3")
        assertEquals(FileChooserAccept.ANY, resolved.primaryType)
        assertEquals(emptyList(), resolved.mimeTypes)
    }

    @Test
    fun knownExtensionsStillFilterWhenAllResolve() {
        // The widening above must not swallow the normal case.
        val resolved = resolve(".png", ".jpg")
        assertEquals("image/*", resolved.primaryType)
        assertEquals(listOf("image/png", "image/jpeg"), resolved.mimeTypes)
    }

    @Test
    fun oneFamilyWidensToThatFamilysWildcard() {
        // accept="image/png,image/jpeg" must still show JPEGs, so the Intent type can't be image/png.
        val resolved = resolve("image/png", "image/jpeg")
        assertEquals("image/*", resolved.primaryType)
        assertEquals(listOf("image/png", "image/jpeg"), resolved.mimeTypes)
    }

    @Test
    fun mixedFamiliesWidenToAny() {
        val resolved = resolve("image/png", "application/pdf")
        assertEquals(FileChooserAccept.ANY, resolved.primaryType)
        assertEquals(listOf("image/png", "application/pdf"), resolved.mimeTypes)
    }

    @Test
    fun commaJoinedEntryIsSplitAgain() {
        // Some WebView versions hand the whole accept attribute back as a single entry.
        val resolved = resolve("image/png,video/mp4")
        assertEquals(FileChooserAccept.ANY, resolved.primaryType)
        assertEquals(listOf("image/png", "video/mp4"), resolved.mimeTypes)
    }

    @Test
    fun duplicatesCollapse() {
        val resolved = resolve(".jpg", ".jpeg", "image/jpeg")
        assertEquals("image/jpeg", resolved.primaryType)
        assertEquals(listOf("image/jpeg"), resolved.mimeTypes)
    }

    @Test
    fun caseAndWhitespaceAreNormalized() {
        val resolved = resolve(" IMAGE/PNG ", ".PNG")
        assertEquals("image/png", resolved.primaryType)
        assertEquals(listOf("image/png"), resolved.mimeTypes)
    }
}

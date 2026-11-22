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
package com.vitorpamplona.amethyst.commons.llamagarden

import com.vitorpamplona.quartz.llamagarden.torrent.ModelTorrentEvent
import com.vitorpamplona.quartz.llamagarden.torrent.tags.FileClassTag
import com.vitorpamplona.quartz.llamagarden.torrent.tags.HfMatchTag
import com.vitorpamplona.quartz.llamagarden.torrent.tags.ModelKindTag
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TorrentSubmissionFormTest {
    private val me = "22".repeat(32)

    private fun TorrentSubmissionForm.published(): ModelTorrentEvent = template(me).let { EventFactory.create("11".repeat(32), me, it.createdAt, it.kind, it.tags, it.content, "00".repeat(64)) }

    @Test
    fun needsANameAndAFetchableUrl() {
        assertFalse(TorrentSubmissionForm(torrentUrl = "https://nostr.download/a.torrent").isPublishable)
        assertFalse(TorrentSubmissionForm(torrentUrl = "nostr.download/a.torrent", name = "m").isPublishable)
        assertFalse(TorrentSubmissionForm(torrentUrl = "javascript:alert(1)", name = "m").isPublishable)
        assertFalse(TorrentSubmissionForm(torrentUrl = "https://", name = "m").isPublishable)
        assertTrue(TorrentSubmissionForm(torrentUrl = " https://nostr.download/a.torrent ", name = "m").isPublishable)
    }

    @Test
    fun claimsNoHuggingFaceMatchWithoutALink() {
        val bare = TorrentSubmissionForm(torrentUrl = "https://a.example/x.torrent", name = "m").published()
        assertNull(bare.hfMatch())

        val linked = TorrentSubmissionForm(torrentUrl = "https://a.example/x.torrent", name = "m", huggingFaceUrl = "https://huggingface.co/a/b", hfMatch = HfMatchTag.NO).published()
        assertEquals(HfMatchTag.NO, linked.hfMatch())
    }

    @Test
    fun dropsWhatHiddenFieldsStillHold() {
        // Picked Quant, a format and Fine-tune, then went back to Base.
        val base =
            TorrentSubmissionForm(torrentUrl = "https://a.example/x.torrent", name = "m", fileClass = FileClassTag.BASE, modelKind = ModelKindTag.FINE_TUNE, quantType = "GGUF")
                .published()
        assertEquals(ModelKindTag.BASE, base.modelKind())
        assertNull(base.quantType())

        val quant =
            TorrentSubmissionForm(torrentUrl = "https://a.example/x.torrent", name = "m", fileClass = FileClassTag.QUANT, modelKind = ModelKindTag.FINE_TUNE, quantType = "GGUF")
                .published()
        assertEquals(ModelKindTag.FINE_TUNE, quant.modelKind())
        assertEquals("GGUF", quant.quantType())
    }

    @Test
    fun sizesAreSiGigabytes() {
        assertEquals(16_000_000_000L, TorrentSubmissionForm(sizeGb = "16").approximateSize())
        assertEquals(1_500_000_000L, TorrentSubmissionForm(sizeGb = "1.5").approximateSize())
        assertNull(TorrentSubmissionForm(sizeGb = "0").approximateSize())
        assertEquals("1.23", TorrentSubmissionForm.cleanSize("1.2.3"))
        assertEquals("16", TorrentSubmissionForm.cleanSize("16 GB"))
    }
}

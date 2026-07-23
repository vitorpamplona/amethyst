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
package com.vitorpamplona.quartz.buzz.media

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Note: Buzz's own `kind.rs` documents 49001 as "Not a relay event kind" (an internal
 * media-upload audit projection), so it is intentionally NOT registered in `EventFactory`.
 * We build the typed event directly from its template rather than via signer dispatch.
 */
class MediaUploadEventTest {
    @Test
    fun buildsBlobDescriptorContentAndHashTag() {
        val uploader = KeyPair().pubKey.toHexKey()
        val sha = "c".repeat(64)
        val payload =
            MediaUploadPayload(
                url = "https://cdn.example.com/$sha.png",
                sha256 = sha,
                size = 1234,
                mimeType = "image/png",
                uploaded = 1_700_000_000,
                dim = "640x480",
            )
        val template = MediaUploadEvent.build(payload, uploaderPubKey = uploader)
        val event = MediaUploadEvent("", uploader, template.createdAt, template.tags, template.content, "")

        assertEquals(49001, event.kind)
        assertEquals(sha, event.sha256Tag())
        assertEquals(uploader, event.uploader())
        val parsed = event.payload()
        assertEquals(payload, parsed)
        // `type` is renamed on the wire; `dim` is present, optional fields are omitted.
        assertEquals("image/png", parsed?.mimeType)
        assertEquals("640x480", parsed?.dim)
    }
}

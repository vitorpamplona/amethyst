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
package com.vitorpamplona.quartz.nip10Notes

import kotlin.test.Test
import kotlin.test.assertEquals

class ThreadRootIdOrSelfTest {
    private val selfId = "3b1ef90115dc6f383e9588d29c86fe3ecda6ecc98415af556cd1399731a15e09"
    private val rootId = "41ed6fdfbe827e7e87f3bfd852270b036c199b721777593d257029d742adcb46"
    private val parentId = "54851588857363fa077fc756c1a64528f511d39476ca37e6a781bcb7aabb9e7d"
    private val pubKey = "b62b357c1b939ae051a361d5befd409ddda06264a7588f1a986c24b1d13cc53f"
    private val relay = "wss://relay.damus.io"
    private val sig = "0".repeat(128)

    private fun textNote(tags: Array<Array<String>>): TextNoteEvent = TextNoteEvent(selfId, pubKey, 1778593701L, tags, "", sig)

    @Test fun topLevelNote_noETags_resolvesToOwnId() {
        // A note with no e-tags is the root of its own thread.
        val note = textNote(emptyArray())
        assertEquals(selfId, note.threadRootIdOrSelf())
    }

    @Test fun replyWithRootMarker_resolvesToRoot() {
        // Modern NIP-10: explicit "root" marker.
        val note = textNote(arrayOf(arrayOf("e", rootId, relay, "root")))
        assertEquals(rootId, note.threadRootIdOrSelf())
    }

    @Test fun replyWithBothRootAndReplyMarkers_resolvesToRoot() {
        // Multi-level reply: "root" marker points to thread root,
        // "reply" marker points to immediate parent.
        val note =
            textNote(
                arrayOf(
                    arrayOf("e", rootId, relay, "root"),
                    arrayOf("e", parentId, relay, "reply"),
                ),
            )
        assertEquals(rootId, note.threadRootIdOrSelf())
    }

    @Test fun replyWithOnlyReplyMarker_resolvesToReplyTarget() {
        // NIP-10 legacy single-level form: a one-level reply marked only
        // "reply" with no "root" marker. The "reply" target IS the
        // conversation root. Regression case from issue #161 device QA.
        val note = textNote(arrayOf(arrayOf("e", rootId, relay, "reply")))
        assertEquals(rootId, note.threadRootIdOrSelf())
    }

    @Test fun replyWithUnmarkedETag_resolvesToTaggedEvent() {
        // Positional NIP-10: e-tag with no marker. Treated as root by
        // BaseThreadedEvent.unmarkedRoot().
        val note = textNote(arrayOf(arrayOf("e", rootId, relay)))
        assertEquals(rootId, note.threadRootIdOrSelf())
    }
}

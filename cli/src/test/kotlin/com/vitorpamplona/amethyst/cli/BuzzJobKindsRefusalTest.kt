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
package com.vitorpamplona.amethyst.cli

import com.vitorpamplona.amethyst.cli.commands.BuzzJobCommands
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PublishResult
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * block/buzz at 52cbc6113 refuses every agent-job kind (43001-43006) from a client with
 * `restricted: unknown event kind` (its ingest has no scope for them); amy must name that, not
 * report a bare rejection, and must not mistake any other refusal for it.
 */
class BuzzJobKindsRefusalTest {
    private val relay = RelayUrlNormalizer.normalize("ws://127.0.0.1:7500")

    @Test
    fun theUnknownKindRefusalIsRecognised() {
        val ack = mapOf(relay to PublishResult(false, "restricted: unknown event kind"))
        assertEquals("restricted: unknown event kind", BuzzJobCommands.jobKindsRefusal(ack))
    }

    @Test
    fun otherRefusalsAreNot() {
        assertNull(BuzzJobCommands.jobKindsRefusal(mapOf(relay to PublishResult(false, "restricted: only members can post in this channel"))))
        assertNull(BuzzJobCommands.jobKindsRefusal(mapOf(relay to PublishResult(true, ""))))
    }
}

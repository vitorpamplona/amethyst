/**
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
package com.vitorpamplona.quartz.nip01Core.jackson

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.verify
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import org.junit.Test

class EventDeserializerTest {
    @Test
    fun testUnsignedEvent() {
        val unsignedEventJson = """{"kind":"1","content":"This is an unsigned event.","created_at":1234,"tags":[]}"""
        val event = Event.fromJson(unsignedEventJson)
        assert(event.id.isEmpty())
        assert(event.pubKey.isEmpty())
        assert(event.sig.isEmpty())
        assert(!event.verify())
    }

    @Test
    fun testSignedEvent() {
        val keyPair = KeyPair()
        val signer = NostrSignerInternal(keyPair)

        val unsignedEvent = TextNoteEvent.build("test")
        val signedEvent = signer.signerSync.sign(unsignedEvent)!!
        val eventJson = signedEvent.toJson()
        val event = Event.fromJson(eventJson)

        assert(event.id.isNotEmpty())
        assert(event.sig.isNotEmpty())
        assert(event.pubKey.isNotEmpty())
        assert(event.verify())
    }
}

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
package com.vitorpamplona.quartz.nip01Core.jackson

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventDeserializerTest {
    @Test
    fun testEventTemplateToJson() {
        val templateJson = """{"created_at":1234,"kind":1,"tags":[],"content":"This is an unsigned event."}"""
        val template = EventTemplate.fromJson(templateJson)

        val json = template.toJson()

        assertEquals(json, templateJson)
    }

    @Test
    fun testEventTemplate() {
        val templateJson = """{"kind":1,"content":"This is an unsigned event.","created_at":1234,"tags":[]}"""
        val template = EventTemplate.fromJson(templateJson)

        assertEquals(1, template.kind)
        assertEquals("This is an unsigned event.", template.content)
        assertTrue(template.tags.isEmpty())
    }

    @Test
    fun testSignedEvent() {
        val keyPair = KeyPair()
        val signer = NostrSignerInternal(keyPair)

        val templateJson = """{"kind":1,"content":"This is an unsigned event.","created_at":1234,"tags":[]}"""
        val template = EventTemplate.fromJson(templateJson)

        val signedEvent = signer.signerSync.sign(template)

        assertTrue(signedEvent.id.isNotEmpty())
        assertTrue(signedEvent.sig.isNotEmpty())
        assertTrue(signedEvent.pubKey.isNotEmpty())
        assertTrue(signedEvent.verify())
    }
}

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
package com.vitorpamplona.quartz.buzz.dm

import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DmOpenEventTest {
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)

    private fun event(template: EventTemplate<DmOpenEvent>) = DmOpenEvent("00", alice, template.createdAt, template.tags, template.content, "sig")

    @Test
    fun buildTagsAndContent() {
        val ev = event(DmOpenEvent.build(listOf(alice, bob)))

        assertEquals(DmOpenEvent.KIND, ev.kind)
        assertEquals("", ev.content)
        assertEquals(listOf(alice, bob), ev.participants())
        assertTrue(ev.tags.all { it[0] == "p" })
    }

    @Test
    fun rejectsEmptyParticipants() {
        assertFailsWith<IllegalArgumentException> { DmOpenEvent.build(emptyList()) }
    }

    @Test
    fun rejectsOverEightParticipants() {
        val nine = (0 until 9).map { it.toString().repeat(64).take(64) }
        assertFailsWith<IllegalArgumentException> { DmOpenEvent.build(nine) }
    }
}

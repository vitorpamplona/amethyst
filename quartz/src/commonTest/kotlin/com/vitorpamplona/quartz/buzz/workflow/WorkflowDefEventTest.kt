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
package com.vitorpamplona.quartz.buzz.workflow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkflowDefEventTest {
    private val workflowId = "7f0000ec-1111-2222-3333-444455556666"
    private val channel = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"
    private val yaml =
        """
        name: "Greeter"
        trigger:
          on: message_posted
        steps:
          - id: s1
            action: send_message
            text: "Hi {{trigger.author}}"
        """.trimIndent()

    @Test
    fun buildWithNameAndChannel() {
        val t = WorkflowDefEvent.build(workflowId, channel, yaml, name = "Greeter")
        val ev = WorkflowDefEvent("00", "f".repeat(64), t.createdAt, t.tags, t.content, "sig")

        assertEquals(30620, ev.kind)
        assertEquals(workflowId, ev.workflowId())
        assertEquals(workflowId, ev.dTag())
        assertEquals(channel, ev.channel())
        assertEquals("Greeter", ev.name())
        assertEquals(yaml, ev.yaml())
        // Addressable identity resolves from the d tag.
        assertEquals(workflowId, ev.address().dTag)
    }

    @Test
    fun nameIsOptional() {
        val t = WorkflowDefEvent.build(workflowId, channel, yaml)
        val ev = WorkflowDefEvent("00", "f".repeat(64), t.createdAt, t.tags, t.content, "sig")

        assertNull(ev.name())
        assertEquals(channel, ev.channel())
    }
}

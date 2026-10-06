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
import kotlin.test.assertTrue

class WorkflowHintProviderTest {
    private val author = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val approver = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val head = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val workflowId = "0b6c3f1e-2d4a-4b5c-8d6e-7f8091a2b3c4"
    private val channel = "6f1a8a3c-9f1e-4d2b-8c1a-2b3c4d5e6f70"
    private val id = "00".repeat(32)
    private val sig = "00".repeat(64)

    @Test
    fun triggerLinksTheAuthorsOwnDefinition() {
        val event = WorkflowTriggerEvent(id, author, 1, arrayOf(arrayOf("d", workflowId)), "", sig)
        assertEquals(listOf("30620:$author:$workflowId"), event.linkedAddressIds())
        assertTrue(event.addressHints().isEmpty())

        assertTrue(WorkflowTriggerEvent(id, author, 1, emptyArray(), "", sig).linkedAddressIds().isEmpty())
    }

    @Test
    fun definitionDoesNotLinkItsOwnSupersededHead() {
        // `expected-revision` is the author's own previous head of this address: a CAS
        // precondition, not a reference worth routing or materializing.
        val template = WorkflowDefEvent.build(workflowId, channel, "steps: []", "Deploy", head)
        val event = WorkflowDefEvent(id, author, template.createdAt, template.tags, template.content, sig)
        assertEquals(head, event.expectedRevision())
        assertTrue(event.linkedEventIds().isEmpty())
        assertTrue(event.eventHints().isEmpty())
    }

    @Test
    fun approvalRequestLinksTheApprover() {
        val template = WorkflowApprovalRequestedEvent.build(channel, approver)
        val event = WorkflowApprovalRequestedEvent(id, author, template.createdAt, template.tags, template.content, sig)
        assertEquals(listOf(approver), event.linkedPubKeys())
    }

    @Test
    fun approvalNotesAreSearchable() {
        assertEquals("looks good", ApprovalGrantEvent(id, author, 1, arrayOf(arrayOf("d", head)), "looks good", sig).indexableContent())
        assertEquals("not this week", ApprovalDenyEvent(id, author, 1, arrayOf(arrayOf("d", head)), "not this week", sig).indexableContent())
    }
}

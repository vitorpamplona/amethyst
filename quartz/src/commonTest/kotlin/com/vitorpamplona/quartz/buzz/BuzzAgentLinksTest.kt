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
package com.vitorpamplona.quartz.buzz

import com.vitorpamplona.quartz.buzz.aeEngrams.EngramEvent
import com.vitorpamplona.quartz.buzz.amTurnMetrics.AgentTurnMetricEvent
import com.vitorpamplona.quartz.buzz.aoObserver.ObserverFrameEvent
import com.vitorpamplona.quartz.buzz.audit.AuditEntryEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleEndedEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleGuidelinesEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleParticipantJoinedEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleParticipantLeftEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleReactionEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleStartedEvent
import com.vitorpamplona.quartz.buzz.jobs.JobAcceptedEvent
import com.vitorpamplona.quartz.buzz.jobs.JobCancelEvent
import com.vitorpamplona.quartz.buzz.jobs.JobErrorEvent
import com.vitorpamplona.quartz.buzz.jobs.JobProgressEvent
import com.vitorpamplona.quartz.buzz.jobs.JobRequestEvent
import com.vitorpamplona.quartz.buzz.jobs.JobResultEvent
import com.vitorpamplona.quartz.buzz.notifications.MemberAddedNotificationEvent
import com.vitorpamplona.quartz.buzz.notifications.MemberRemovedNotificationEvent
import com.vitorpamplona.quartz.buzz.pairing.PairingEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowApprovalDeniedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowApprovalGrantedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowApprovalRequestedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowCancelledEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowCompletedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowDefEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowFailedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowStepCompletedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowStepFailedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowStepStartedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowTriggerEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowTriggeredEvent
import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import kotlin.test.Test
import kotlin.test.assertEquals

class BuzzAgentLinksTest {
    private val id = "0".repeat(64)
    private val author = "a".repeat(64)
    private val sig = "0".repeat(128)
    private val p1 = "1".repeat(64)
    private val p2 = "2".repeat(64)
    private val e1 = "e1".repeat(32)
    private val channel = "7c1f0e2a-3b4d-4e5f-8a9b-0c1d2e3f4a5b"

    private val h = arrayOf("h", channel)
    private val group = Link(Relation.GROUP, LinkTarget.Tag("h", channel), "h")

    @Test
    fun jobLifecycle() {
        assertEquals(
            listOf(group, Link(Relation.AGENT, LinkTarget.User(p1), "p")),
            JobRequestEvent(id, author, 0, arrayOf(h, arrayOf("p", p1)), "summarize", sig).links(),
        )
        assertEquals(
            listOf(
                Link(Relation.REQUEST, LinkTarget.Event(e1), "e"),
                group,
                Link(Relation.REQUEST_AUTHOR, LinkTarget.User(p2), "p"),
            ),
            JobAcceptedEvent(id, author, 0, arrayOf(arrayOf("e", e1), h, arrayOf("p", p2)), "", sig).links(),
        )
        assertEquals(
            listOf(Link(Relation.REQUEST, LinkTarget.Event(e1), "e", mapOf("status" to "running")), group),
            JobProgressEvent(id, author, 0, arrayOf(arrayOf("e", e1), h, arrayOf("status", "running")), "50%", sig).links(),
        )
        assertEquals(
            listOf(
                Link(Relation.REQUEST, LinkTarget.Event(e1), "e", mapOf("status" to "success")),
                group,
                Link(Relation.REQUEST_AUTHOR, LinkTarget.User(p2), "p"),
            ),
            JobResultEvent(id, author, 0, arrayOf(arrayOf("e", e1), h, arrayOf("p", p2), arrayOf("status", "success")), "done", sig).links(),
        )
        assertEquals(
            listOf(Link(Relation.REQUEST, LinkTarget.Event(e1), "e")),
            JobCancelEvent(id, author, 0, arrayOf(arrayOf("e", e1)), "", sig).links(),
        )
        assertEquals(
            listOf(
                Link(Relation.REQUEST, LinkTarget.Event(e1), "e"),
                group,
                Link(Relation.REQUEST_AUTHOR, LinkTarget.User(p2), "p"),
            ),
            JobErrorEvent(id, author, 0, arrayOf(arrayOf("e", e1), h, arrayOf("p", p2)), "boom", sig).links(),
        )
    }

    @Test
    fun workflowLifecycleEventsLinkTheirChannel() {
        val tags = arrayOf(h)
        listOf(
            WorkflowDefEvent(id, author, 0, arrayOf(arrayOf("d", "wf-1"), h, arrayOf("name", "Deploy")), "steps: []", sig),
            WorkflowTriggeredEvent(id, author, 0, tags, "", sig),
            WorkflowStepStartedEvent(id, author, 0, tags, "", sig),
            WorkflowStepCompletedEvent(id, author, 0, tags, "", sig),
            WorkflowStepFailedEvent(id, author, 0, tags, "", sig),
            WorkflowCompletedEvent(id, author, 0, tags, "", sig),
            WorkflowFailedEvent(id, author, 0, tags, "", sig),
            WorkflowCancelledEvent(id, author, 0, tags, "", sig),
            WorkflowApprovalGrantedEvent(id, author, 0, tags, "", sig),
            WorkflowApprovalDeniedEvent(id, author, 0, tags, "", sig),
        ).forEach { assertEquals(listOf(group), it.links(), it::class.simpleName) }

        assertEquals(
            listOf(group, Link(Relation.APPROVER, LinkTarget.User(p1), "p")),
            WorkflowApprovalRequestedEvent(id, author, 0, arrayOf(h, arrayOf("p", p1)), "", sig).links(),
        )
    }

    @Test
    fun workflowTriggerNamesTheOwnersDefinition() {
        assertEquals(
            listOf(Link(Relation.TRIGGERED, LinkTarget.Address("30620:$author:wf-1"), "d")),
            WorkflowTriggerEvent(id, author, 0, arrayOf(arrayOf("d", "wf-1")), "{}", sig).links(),
        )
        assertEquals(emptyList(), WorkflowTriggerEvent(id, author, 0, arrayOf(), "", sig).links())
    }

    @Test
    fun huddles() {
        val participant = Link(Relation.PARTICIPANT, LinkTarget.User(p1), "p")
        val content = """{"ephemeral_channel_id":"huddle-1"}"""
        assertEquals(listOf(group), HuddleStartedEvent(id, author, 0, arrayOf(h), content, sig).links())
        assertEquals(listOf(group, participant), HuddleParticipantJoinedEvent(id, author, 0, arrayOf(h, arrayOf("p", p1)), content, sig).links())
        assertEquals(listOf(group, participant), HuddleParticipantLeftEvent(id, author, 0, arrayOf(h, arrayOf("p", p1)), content, sig).links())
        assertEquals(listOf(group, participant), HuddleEndedEvent(id, author, 0, arrayOf(h, arrayOf("p", p1)), content, sig).links())
        assertEquals(listOf(group), HuddleEndedEvent(id, author, 0, arrayOf(h), content, sig).links())
        assertEquals(listOf(group), HuddleGuidelinesEvent(id, author, 0, arrayOf(h), "Be kind", sig).links())
        assertEquals(
            listOf(group),
            HuddleReactionEvent(id, author, 0, arrayOf(h, arrayOf("reaction", "🎉"), arrayOf("sender_name", "Ann")), "🎉", sig).links(),
        )
    }

    @Test
    fun membershipNotificationsIgnoreTheContentActor() {
        val body = """{"type":"member_added","channel_id":"$channel","actor":"$p2"}"""
        assertEquals(
            listOf(Link(Relation.ADDED_USER, LinkTarget.User(p1), "p"), group),
            MemberAddedNotificationEvent(id, author, 0, arrayOf(arrayOf("p", p1), h), body, sig).links(),
        )
        assertEquals(
            listOf(Link(Relation.REMOVED_USER, LinkTarget.User(p1), "p"), group),
            MemberRemovedNotificationEvent(id, author, 0, arrayOf(arrayOf("p", p1), h), body, sig).links(),
        )
    }

    @Test
    fun engramLinksItsOwnerButNeverItsBlindedD() {
        val blinded = "b".repeat(64)
        assertEquals(
            listOf(Link(Relation.OWNER, LinkTarget.User(p1), "p")),
            EngramEvent(id, author, 0, arrayOf(arrayOf("d", blinded), arrayOf("p", p1)), "ciphertext", sig).links(),
        )
    }

    @Test
    fun agentTelemetry() {
        assertEquals(
            listOf(
                Link(Relation.OWNER, LinkTarget.User(p1), "p"),
                Link(Relation.AGENT, LinkTarget.User(author), "agent"),
            ),
            AgentTurnMetricEvent(id, author, 0, arrayOf(arrayOf("p", p1), arrayOf("agent", author)), "ciphertext", sig).links(),
        )
        val frame = mapOf("frame" to "control")
        assertEquals(
            listOf(
                Link(Relation.RECIPIENT, LinkTarget.User(p2), "p", frame),
                Link(Relation.AGENT, LinkTarget.User(p2), "agent", frame),
            ),
            ObserverFrameEvent(id, author, 0, arrayOf(arrayOf("p", p2), arrayOf("agent", p2), arrayOf("frame", "control")), "ciphertext", sig).links(),
        )
        assertEquals(
            listOf(Link(Relation.RECIPIENT, LinkTarget.User(p1), "p")),
            PairingEvent(id, author, 0, arrayOf(arrayOf("p", p1)), "ciphertext", sig).links(),
        )
    }

    @Test
    fun auditObjectIsAnEventOnlyForEventActions() {
        assertEquals(
            listOf(
                Link(Relation.ACTOR, LinkTarget.User(p1), "p"),
                Link(Relation.AUDITED, LinkTarget.Event(e1), "object", mapOf("action" to "event_deleted")),
            ),
            AuditEntryEvent(id, author, 0, arrayOf(arrayOf("action", "event_deleted"), arrayOf("p", p1), arrayOf("object", e1)), "{}", sig).links(),
        )
        // A media sha256 is 64-hex too: it must stay a plain value.
        val sha256 = "5".repeat(64)
        assertEquals(
            listOf(Link(Relation.AUDITED, LinkTarget.Tag("object", sha256), "object", mapOf("action" to "media_uploaded"))),
            AuditEntryEvent(id, author, 0, arrayOf(arrayOf("action", "media_uploaded"), arrayOf("object", sha256)), "{}", sig).links(),
        )
    }
}

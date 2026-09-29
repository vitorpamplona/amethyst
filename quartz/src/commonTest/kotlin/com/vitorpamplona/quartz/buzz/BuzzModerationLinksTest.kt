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

import com.vitorpamplona.quartz.buzz.iaIdentityArchival.ArchiveRequestEvent
import com.vitorpamplona.quartz.buzz.iaIdentityArchival.ArchivedIdentitiesListEvent
import com.vitorpamplona.quartz.buzz.iaIdentityArchival.ArchivedIdentityEvent
import com.vitorpamplona.quartz.buzz.iaIdentityArchival.UnarchiveRequestEvent
import com.vitorpamplona.quartz.buzz.iaIdentityArchival.UnarchivedIdentityEvent
import com.vitorpamplona.quartz.buzz.moderation.ModerationBanEvent
import com.vitorpamplona.quartz.buzz.moderation.ModerationResolveReportEvent
import com.vitorpamplona.quartz.buzz.moderation.ModerationTimeoutEvent
import com.vitorpamplona.quartz.buzz.moderation.ModerationUntimeoutEvent
import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.OwnerAttestation
import com.vitorpamplona.quartz.buzz.relayAdmin.RelayAdminAddMemberEvent
import com.vitorpamplona.quartz.buzz.relayAdmin.RelayAdminChangeRoleEvent
import com.vitorpamplona.quartz.buzz.relayAdmin.RelayAdminRemoveMemberEvent
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.props.ActorProps
import com.vitorpamplona.quartz.nip01Core.links.props.ModerationProps
import com.vitorpamplona.quartz.nip01Core.links.props.OwnerProps
import com.vitorpamplona.quartz.nip01Core.links.props.ResolutionProps
import com.vitorpamplona.quartz.nip01Core.links.props.RoleProps
import kotlin.test.Test
import kotlin.test.assertEquals

class BuzzModerationLinksTest {
    private val id = "0".repeat(64)
    private val author = "a".repeat(64)
    private val sig = "0".repeat(128)
    private val p1 = "1".repeat(64)
    private val p2 = "2".repeat(64)
    private val p3 = "3".repeat(64)
    private val e1 = "e1".repeat(32)

    @Test
    fun archivedIdentityDelta() {
        val event =
            ArchivedIdentityEvent(
                id,
                author,
                0,
                arrayOf(
                    arrayOf("-"),
                    arrayOf("p", p1),
                    arrayOf("consent", "owner", p2),
                    arrayOf("e", e1),
                    arrayOf("reason", "rotated"),
                    arrayOf("replaced-by", p3),
                ),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.ARCHIVED, LinkTarget.User(p1), "p", ModerationProps(reason = "rotated")),
                Link(Relation.ACTOR, LinkTarget.User(p2), "consent", ActorProps("owner")),
                Link(Relation.REQUEST, LinkTarget.Event(e1), "e"),
                Link(Relation.REPLACED_BY, LinkTarget.User(p3), "replaced-by"),
            ),
            event.links(),
        )
    }

    @Test
    fun unarchivedIdentityDeltaWithoutReason() {
        val event =
            UnarchivedIdentityEvent(
                id,
                author,
                0,
                arrayOf(arrayOf("-"), arrayOf("p", p1), arrayOf("consent", "self", p1), arrayOf("e", e1)),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.UNARCHIVED, LinkTarget.User(p1), "p"),
                Link(Relation.ACTOR, LinkTarget.User(p1), "consent", ActorProps("self")),
                Link(Relation.REQUEST, LinkTarget.Event(e1), "e"),
            ),
            event.links(),
        )
    }

    @Test
    fun archiveRequestLinksTheOwnerOnlyWhenTheAttestationVerifies() {
        val owner = KeyPair()
        val attestation = OwnerAttestation.sign(author, "kind=9035", owner.privKey!!)
        val tags =
            arrayOf(
                arrayOf("-"),
                arrayOf("p", p1),
                arrayOf("replaced-by", p2),
                attestation.toTag(),
            )

        assertEquals(
            listOf(
                Link(Relation.ARCHIVED, LinkTarget.User(p1), "p"),
                Link(Relation.REPLACED_BY, LinkTarget.User(p2), "replaced-by"),
                Link(Relation.OWNER, LinkTarget.User(owner.pubKey.toHexKey()), "auth", OwnerProps("kind=9035")),
            ),
            ArchiveRequestEvent(id, author, 0, tags, "", sig).links(),
        )

        // The same tag on another author's event is a forged claim: no OWNER.
        assertEquals(
            listOf(
                Link(Relation.ARCHIVED, LinkTarget.User(p1), "p"),
                Link(Relation.REPLACED_BY, LinkTarget.User(p2), "replaced-by"),
            ),
            ArchiveRequestEvent(id, p3, 0, tags, "", sig).links(),
        )
    }

    @Test
    fun unarchiveRequest() {
        val owner = KeyPair()
        val attestation = OwnerAttestation.sign(author, "", owner.privKey!!)
        val event =
            UnarchiveRequestEvent(
                id,
                author,
                0,
                arrayOf(arrayOf("-"), arrayOf("p", p1), arrayOf("reason", "mistake"), attestation.toTag()),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.UNARCHIVED, LinkTarget.User(p1), "p", ModerationProps(reason = "mistake")),
                Link(Relation.OWNER, LinkTarget.User(owner.pubKey.toHexKey()), "auth"),
            ),
            event.links(),
        )
    }

    @Test
    fun archivedIdentitiesListDropsMalformedKeys() {
        val event =
            ArchivedIdentitiesListEvent(
                id,
                author,
                0,
                arrayOf(arrayOf("-"), arrayOf("p", p1), arrayOf("p", "not-a-key"), arrayOf("p", p2)),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.ARCHIVED, LinkTarget.User(p1), "p"),
                Link(Relation.ARCHIVED, LinkTarget.User(p2), "p"),
            ),
            event.links(),
        )
    }

    @Test
    fun relayAdminCommands() {
        assertEquals(
            listOf(Link(Relation.ADDED_USER, LinkTarget.User(p1), "p", RoleProps(listOf("admin")))),
            RelayAdminAddMemberEvent(id, author, 0, arrayOf(arrayOf("p", p1), arrayOf("role", "admin")), "", sig).links(),
        )
        assertEquals(
            listOf(Link(Relation.ADDED_USER, LinkTarget.User(p1), "p")),
            RelayAdminAddMemberEvent(id, author, 0, arrayOf(arrayOf("p", p1)), "", sig).links(),
        )
        assertEquals(
            listOf(Link(Relation.REMOVED_USER, LinkTarget.User(p1), "p")),
            RelayAdminRemoveMemberEvent(id, author, 0, arrayOf(arrayOf("p", p1)), "", sig).links(),
        )
        assertEquals(
            listOf(Link(Relation.ROLE_CHANGED, LinkTarget.User(p1), "p", RoleProps(listOf("member")))),
            RelayAdminChangeRoleEvent(id, author, 0, arrayOf(arrayOf("p", p1), arrayOf("role", "member")), "", sig).links(),
        )
    }

    @Test
    fun moderationCommands() {
        assertEquals(
            listOf(Link(Relation.BANNED, LinkTarget.User(p1), "p", ModerationProps(reason = "spam", expiration = 1700000000L))),
            ModerationBanEvent(
                id,
                author,
                0,
                arrayOf(arrayOf("p", p1), arrayOf("expiration", "1700000000"), arrayOf("reason", "spam")),
                "",
                sig,
            ).links(),
        )
        assertEquals(
            listOf(Link(Relation.BANNED, LinkTarget.User(p1), "p")),
            ModerationBanEvent(id, author, 0, arrayOf(arrayOf("p", p1)), "", sig).links(),
        )
        assertEquals(
            listOf(Link(Relation.TIMED_OUT, LinkTarget.User(p1), "p", ModerationProps(expiration = 1700000000L))),
            ModerationTimeoutEvent(id, author, 0, arrayOf(arrayOf("p", p1), arrayOf("expiration", "1700000000")), "", sig).links(),
        )
        assertEquals(
            listOf(Link(Relation.TIMEOUT_CLEARED, LinkTarget.User(p1), "p")),
            ModerationUntimeoutEvent(id, author, 0, arrayOf(arrayOf("p", p1)), "", sig).links(),
        )
    }

    @Test
    fun resolveReportLinksTheReportFromItsOwnTag() {
        val event =
            ModerationResolveReportEvent(
                id,
                author,
                0,
                arrayOf(arrayOf("report", e1), arrayOf("status", "resolved"), arrayOf("action", "ban")),
                "",
                sig,
            )
        assertEquals(
            listOf(Link(Relation.RESOLVED, LinkTarget.Event(e1), "report", ResolutionProps(status = "resolved", action = "ban"))),
            event.links(),
        )
    }
}

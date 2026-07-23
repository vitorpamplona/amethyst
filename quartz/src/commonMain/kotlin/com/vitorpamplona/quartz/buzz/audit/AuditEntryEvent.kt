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
package com.vitorpamplona.quartz.buzz.audit

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz audit-log entry (`kind:48001`): a record of one moderated/administrative action.
 *
 * SCHEMA INFERRED — FLAGGED. The Rust source (`buzz-core/src/kind.rs`) reserves this kind and
 * lists it in a metrics-label allowlist (`buzz-relay/src/handlers/event.rs::bounded_kind_label`),
 * but the codebase NEVER constructs a `kind:48001` Nostr event: the audit trail is a
 * server-side hash-chained DB structure (`buzz-audit/src/entry.rs::AuditEntry`), not a wire
 * event. The tag/content mapping below is a Quartz-side projection of that struct — `content`
 * holds the arbitrary JSON `detail`, an `action` tag holds the [AuditAction], an optional `p`
 * tag names the actor, and an optional `object` tag holds the object id. Do NOT assume this is
 * registrable or interoperable until the Buzz side actually emits this kind on the wire.
 */
@Immutable
class AuditEntryEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The recorded action — the `action` tag. */
    fun action(): AuditAction? = tags.auditAction()

    /** The actor pubkey — the `p` tag, if the action has one. */
    fun actor(): HexKey? = tags.auditActor()

    /** The identifier of the object acted upon — the `object` tag, if any. */
    fun objectId(): String? = tags.auditObjectId()

    /** The arbitrary JSON `detail` context — the event `content`. */
    fun detail(): String = content

    companion object {
        const val KIND = 48001

        fun build(
            action: AuditAction,
            detail: String,
            actorPubKey: HexKey? = null,
            objectId: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<AuditEntryEvent>.() -> Unit = {},
        ) = eventTemplate<AuditEntryEvent>(KIND, detail, createdAt) {
            action(action)
            actorPubKey?.let { actor(it) }
            objectId?.let { objectId(it) }
            initializer()
        }
    }
}

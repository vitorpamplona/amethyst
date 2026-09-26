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
package com.vitorpamplona.quartz.cordn.spec00Coordinator

import com.vitorpamplona.quartz.contextvm.transport.DualSigner

/**
 * The coordinator's eleven MCP tools, and which identity each one rides.
 *
 * Names and argument keys are from
 * `packages/core/src/contracts.ts` — the reference implementation's zod
 * schemas, which are the only normative statement of the wire shape
 * (`spec/00.md` describes the model, not the field names).
 *
 * ## The identity column is the privacy model
 *
 * Every call carries an authenticated caller pubkey that the coordinator
 * derives from the signed inner 25910 event, so "which key signed this" is not
 * something a client can decline to answer — only something it can choose.
 * cordn-web splits them deliberately, and this enum records that split so a
 * caller cannot get it wrong by omission: a stable-identity call is one where
 * the coordinator MUST know who you are (it is your inbox, or your KeyPackage,
 * or your request to join), and everything else rides a throwaway.
 *
 * The split is real but partial, and §8 of the plan has the detail. Two things
 * worth knowing at the call site:
 *
 * - **Admission is in the clear on both ends.** `join_request_store` names
 *   your real key and the `gid`; `welcome_store` names the target's real key
 *   and `welcome_take` is called by it. For any group joined through a share
 *   link the coordinator observes real-identity membership directly. That is
 *   structural, not a bug to route around.
 * - **The ephemeral key is per session, not per message.** One pseudonym posts,
 *   fetches and subscribes across every group you hold on that coordinator for
 *   the life of the session, so the `gid` set it touches is a stable
 *   fingerprint linking those groups together. [CoordinatorClient] takes the
 *   throwaway signer from its caller precisely so the caller can decide how
 *   often to rotate it.
 */
enum class CoordinatorMethod(
    val wire: String,
    val identity: DualSigner.Identity,
) {
    /** Publish a KeyPackage. The publication payload IS this call's signed event. */
    KP_PUBLISH("kp_publish", DualSigner.Identity.STABLE),

    /** Withdraw published KeyPackages. Authorized by the caller being their owner. */
    KP_REMOVE("kp_remove", DualSigner.Identity.STABLE),

    /** Drain this account's pending Welcomes. It is our own inbox. */
    WELCOME_TAKE("welcome_take", DualSigner.Identity.STABLE),

    /** Ask to join a group. The whole point is to name who is asking. */
    JOIN_REQUEST_STORE("join_request_store", DualSigner.Identity.STABLE),

    /** Look up someone's KeyPackages. Reveals "who is being added to a group". */
    KP_LIST("kp_list", DualSigner.Identity.EPHEMERAL),

    /** Consume a KeyPackage by ref. */
    KP_TAKE("kp_take", DualSigner.Identity.EPHEMERAL),

    /** Leave a Welcome for someone. Names the target, not the sender. */
    WELCOME_STORE("welcome_store", DualSigner.Identity.EPHEMERAL),

    /** Drain join requests for groups we administer. */
    JOIN_REQUEST_TAKE_MANY("join_request_take_many", DualSigner.Identity.EPHEMERAL),

    /** Post a sealed payload. */
    MSG_POST("msg_post", DualSigner.Identity.EPHEMERAL),

    /** Catch up on a group's history. */
    MSG_FETCH_MANY("msg_fetch_many", DualSigner.Identity.EPHEMERAL),

    /** Live delivery, over a CEP-41 open stream. */
    MSG_SUB_MANY("msg_sub_many", DualSigner.Identity.EPHEMERAL),
}

/** Argument and result field names, from `packages/core/src/contracts.ts`. */
object CoordinatorFields {
    const val KP_REF = "kp_ref"
    const val KP_REFS = "kp_refs"
    const val KP_64 = "kp_64"
    const val ID = "id"
    const val PK = "pk"
    const val LAST_RESORT = "last_resort"
    const val AT = "at"
    const val EVENT = "event"
    const val KEY_PACKAGE = "keyPackage"
    const val KEY_PACKAGES = "keyPackages"
    const val TARGET_PK = "target_pk"
    const val WELCOME_64 = "welcome_64"
    const val WELCOMES = "welcomes"
    const val CONSUMED = "consumed"
    const val GID = "gid"
    const val GROUPS = "groups"
    const val REQUESTS = "requests"
    const val MSG_64 = "msg_64"
    const val MESSAGES = "messages"
    const val CURSOR = "cursor"
    const val AFTER = "after"
    const val SUBSCRIBED = "subscribed"

    /** MCP puts a tool's typed output here; `content` carries the display form. */
    const val STRUCTURED_CONTENT = "structuredContent"
}

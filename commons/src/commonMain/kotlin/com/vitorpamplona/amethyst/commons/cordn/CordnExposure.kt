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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * What the delivery operator learns, as data rather than as documentation.
 *
 * §8 of `quartz/plans/2026-09-17-cordn-interop.md` ends with a requirement:
 * *"This section should be surfaced in the UI if we ship this, not buried. A
 * Marmot group and a cordn group have materially different metadata exposure
 * and users cannot infer that from either one looking like a group chat."*
 *
 * A comment cannot satisfy that; a screen can, and a screen needs a model. So
 * the analysis lives here as [GroupExposure], computed from the group's actual
 * state rather than written out per group — a group joined from a share link
 * really does expose more than one whose members were added directly, and the
 * difference is mechanical, not editorial.
 *
 * The one thing this deliberately does **not** do is rank the two bindings.
 * cordn is weaker against the delivery operator and stronger against the
 * network (§8.5: the coordinator never sees an IP). Which trade is right
 * depends on who runs the coordinator, which is the user's call and not ours.
 */
enum class ExposureLevel {
    /** The operator cannot learn this at all. */
    NONE,

    /** Learned, but tied to a throwaway key rather than to the account. */
    PSEUDONYMOUS,

    /** Learned and tied to the real npub. */
    IDENTIFIED,
}

/** One thing worth telling the user about, with the rule it follows from. */
enum class ExposureNote {
    /**
     * §8.1 — admission names both ends. `join_request_store` rides the stable
     * identity and carries the `gid`; `welcome_store` names the target's real
     * pubkey. For a group joined from a share link the coordinator observes
     * real-identity membership directly. Structural, not a bug.
     */
    MEMBERSHIP_IS_IDENTIFIED,

    /**
     * §8.2 — the ephemeral identity is per-session, not per-message, so one
     * pseudonym posts and fetches across every group on that coordinator. The
     * set of `gid`s it touches links those groups together and leaks how many
     * there are. This is the one the user cannot guess and the reason a plain
     * "messages are pseudonymous" would be a lie by omission.
     */
    GROUPS_LINKED_BY_SESSION,

    /**
     * §8.3 — one operator holds the complete ordered history of every group it
     * serves, with timestamps and payload sizes, in one SQLite file. Relays
     * are redundant and partitioned; a coordinator is neither.
     */
    SINGLE_OPERATOR_HOLDS_HISTORY,

    /**
     * §8.4 — `kp_publish` rides the stable identity and the coordinator keeps
     * the signed event, by design, to re-serve. That is a verifiable record
     * that this account uses cordn, rotation cadence included.
     */
    PUBLICATION_IS_A_SIGNED_RECORD,

    /**
     * §8.3 — nothing in `spec/03.md` pads the sealed payload, so message sizes
     * are visible to the operator.
     */
    MESSAGE_SIZES_UNPADDED,

    /**
     * §8.6 — the transport is NOT pinned to required encryption, so a
     * coordinator that declines to announce `support_encryption` would receive
     * plaintext JSON-RPC on public relays. Should be impossible in our client;
     * if this ever appears, it is a bug, not a disclosure.
     */
    ENCRYPTION_NOT_PINNED,
}

/**
 * The exposure of one cordn group on one coordinator.
 *
 * @param linkedGroupCount how many groups share this coordinator's ephemeral
 *   session (§8.2). One is already a link between that group and the account's
 *   traffic; more is a graph.
 */
data class GroupExposure(
    val coordinator: HexKey,
    val linkedGroupCount: Int,
    val joinedFromShareLink: Boolean,
    val publishedKeyPackage: Boolean,
    val encryptionPinned: Boolean,
) {
    /** §8: double-sealed, and the coordinator is forbidden from parsing. Always. */
    val content: ExposureLevel = ExposureLevel.NONE

    /** §8.1. Admission names real keys on both ends; there is no other way in. */
    val membership: ExposureLevel = ExposureLevel.IDENTIFIED

    /** §8.2. The ephemeral identity covers the message path — and only that. */
    val messaging: ExposureLevel = ExposureLevel.PSEUDONYMOUS

    fun notes(): List<ExposureNote> =
        buildList {
            add(ExposureNote.MEMBERSHIP_IS_IDENTIFIED)
            add(ExposureNote.SINGLE_OPERATOR_HOLDS_HISTORY)
            add(ExposureNote.MESSAGE_SIZES_UNPADDED)
            // Only worth saying once there is actually something to link to.
            if (linkedGroupCount > 1) add(ExposureNote.GROUPS_LINKED_BY_SESSION)
            if (publishedKeyPackage) add(ExposureNote.PUBLICATION_IS_A_SIGNED_RECORD)
            if (!encryptionPinned) add(ExposureNote.ENCRYPTION_NOT_PINNED)
        }

    /**
     * Whether this group's exposure differs from a Marmot group's in a way the
     * user should be told before they treat the two the same.
     *
     * Always true today, and written as a function anyway: the interesting case
     * is a self-hosted coordinator, where the answer changes without any of
     * this code changing.
     */
    fun differsFromMarmot(): Boolean = true
}

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
package com.vitorpamplona.quartz.concord.cord04Roles

/**
 * The protocol's size caps on Control Plane entities (CORD-04 §2, CORD-02 §6), counted as
 * UTF-8 bytes. They are **read-side rules as well as write-side ones**: a writer refuses to
 * mint an edition past them, and the fold drops (or, for the role counts, trims) whatever
 * another client minted past them, exactly as the reference client (Armada `roles.ts` /
 * `control.ts`) does, so both converge on the same state.
 */
object ConcordLimits {
    /** The protocol-wide name cap: Roles, Channels and the Community name (CORD-02 §6). */
    const val NAME_MAX_BYTES = 64

    /** The Community description cap (CORD-02 §6). */
    const val DESCRIPTION_MAX_BYTES = 10_000

    /** A member holds at most this many Roles; a Grant's extra `role_ids` are ignored (CORD-04 §2). */
    const val MAX_ROLES_PER_MEMBER = 64

    /** A Community folds at most this many Roles: the lowest `role_id`s win (CORD-04 §2). */
    const val MAX_ROLES_PER_COMMUNITY = 100

    fun utf8Size(s: String): Int = s.encodeToByteArray().size

    fun nameFits(name: String): Boolean = utf8Size(name) <= NAME_MAX_BYTES

    fun descriptionFits(description: String?): Boolean = description == null || utf8Size(description) <= DESCRIPTION_MAX_BYTES

    /** Whether [metadata] is within the Community metadata caps (CORD-02 §6). */
    fun metadataFits(metadata: MetadataEntity): Boolean = nameFits(metadata.name) && descriptionFits(metadata.description)
}

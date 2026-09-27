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

import com.vitorpamplona.quartz.cordn.appGroupRef.CordnGroupRef

/**
 * What a pasted `cordn1…` link turns out to be.
 *
 * A group ref is the one cordn artifact a person handles directly — it arrives
 * in a chat message or a QR code — and it is also the moment where §8 matters:
 * before joining, not after. So the parse and the disclosure are computed
 * together here, in headless code a test can drive, and the screen only draws
 * the result.
 */
sealed interface CordnLinkInspection {
    /** Not a cordn link, with the rule it broke, in the decoder's own words. */
    data class Invalid(
        val reason: String,
    ) : CordnLinkInspection

    /**
     * A well-formed link.
     *
     * [coordinator] and [exposure] are null together, and legitimately so:
     * `spec/applications/group-ref.md` §2 makes the coordinator optional, and a
     * ref carrying only a `gid` names no operator — so there is no one to
     * disclose anything about, and pretending otherwise would be inventing a
     * threat model for a server we cannot identify.
     */
    data class Valid(
        val ref: CordnGroupRef,
        val coordinator: CoordinatorConfig?,
        val exposure: GroupExposure?,
    ) : CordnLinkInspection {
        /** Whether this link can be acted on without asking the sender for more. */
        val isFollowable: Boolean get() = coordinator != null
    }

    companion object {
        /**
         * Inspects [input].
         *
         * @param existingGroupsOnCoordinator how many groups this account
         *   already has on that coordinator, so §8.2's linkage count describes
         *   what joining would actually create rather than a hypothetical. The
         *   default assumes none, which is the conservative reading — it
         *   under-reports linkage rather than inventing it.
         */
        fun of(
            input: String,
            existingGroupsOnCoordinator: Int = 0,
            publishedKeyPackage: Boolean = false,
        ): CordnLinkInspection {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return Invalid("empty")

            val ref =
                try {
                    CordnGroupRef.decode(trimmed)
                } catch (e: IllegalArgumentException) {
                    return Invalid(e.message ?: "not a cordn group reference")
                }

            val coordinator = CoordinatorConfig.from(ref)
            return Valid(
                ref = ref,
                coordinator = coordinator,
                exposure =
                    coordinator?.let {
                        GroupExposure(
                            coordinator = it.pubKey,
                            // The group being inspected is the one that would be added.
                            linkedGroupCount = existingGroupsOnCoordinator + 1,
                            // A link is how a stranger joins, which is exactly the
                            // `join_request_store` path §8.1 describes.
                            joinedFromShareLink = true,
                            publishedKeyPackage = publishedKeyPackage,
                            encryptionPinned = true,
                        )
                    },
            )
        }
    }
}

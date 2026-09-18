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
package com.vitorpamplona.quartz.contextvm.cep35Discovery

import com.vitorpamplona.quartz.contextvm.cep06Announcements.DiscoverySurface
import com.vitorpamplona.quartz.nip01Core.core.Tag

/**
 * Per-session capability learning (CEP-35).
 *
 * Discovery is a first-message exchange in each direction, not an
 * initialize-only step: a server-to-client message that is not an initialize
 * response may still carry the server's baseline. After that exchange, later
 * feature tags are message-local and do not mutate the baseline unless a
 * feature-specific CEP says they do — CEP-8's `payment_interaction` upsert being
 * the one that does.
 */
class SessionDiscovery {
    private var baseline: DiscoverySurface? = null

    /** The peer's learned baseline, or null before their first message. */
    val peer get() = baseline

    val hasLearned get() = baseline != null

    /**
     * Applies a peer message's tags.
     *
     * The first one establishes the baseline; later ones are returned for
     * message-local interpretation but leave the baseline alone.
     */
    fun observe(tags: Array<Tag>): DiscoverySurface {
        val surface = DiscoverySurface.parse(tags)
        if (baseline == null) baseline = surface
        return surface
    }

    /** Replaces the baseline outright. For a feature CEP that defines an update. */
    fun replaceBaseline(tags: Array<Tag>) {
        baseline = DiscoverySurface.parse(tags)
    }
}

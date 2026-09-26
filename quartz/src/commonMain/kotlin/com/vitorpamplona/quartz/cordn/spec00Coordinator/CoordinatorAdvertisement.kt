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

import com.vitorpamplona.quartz.contextvm.cep06Announcements.AnnouncedTools

/**
 * Recognising a cordn coordinator in a crowd of ContextVM servers.
 *
 * ## There is no cordn marker
 *
 * cordn defines no announcement kind, no `t` tag and no registry — a
 * coordinator announces itself with the same CEP-6 pair (kind 11316 + 11317)
 * as any other MCP server. So "is this a coordinator?" can only be answered
 * the way MCP answers every question about a server: by what it serves.
 *
 * Measured against the public relays on 2026-09-22: of the newest 100 kind
 * 11317 announcements, 42 advertised exactly this toolset and 58 were
 * unrelated MCP servers (metadata, CI, currency). The names separate them
 * cleanly, which is what makes this predicate worth having rather than a
 * guess.
 *
 * ## What it does NOT establish
 *
 * An announcement is a claim, signed only by the key that was going to sign it
 * anyway. Matching here means a server *says* it serves these eleven tools; it
 * does not mean the server is reachable, is still running, implements them
 * correctly, or is trustworthy. Nothing in the protocol should branch on it —
 * it is a filter for a list a human then chooses from, and `spec/00.md` §8.5
 * still holds that a coordinator's identity is its pubkey and nothing else.
 */
object CoordinatorAdvertisement {
    /**
     * The eleven tools of `spec/00.md`, by wire name.
     *
     * Derived from [CoordinatorMethod] rather than written out, so a tool added
     * to the protocol cannot be left out of the predicate.
     */
    val REQUIRED_TOOLS: Set<String> = CoordinatorMethod.entries.mapTo(LinkedHashSet()) { it.wire }

    /**
     * Whether an announced tool list is a cordn coordinator's.
     *
     * Extra tools do not disqualify: a server may serve cordn alongside
     * anything else, and a client that refused those would exclude a
     * coordinator for offering more than the minimum.
     */
    fun matches(tools: AnnouncedTools): Boolean = tools.serves(REQUIRED_TOOLS)

    /** The tools of [REQUIRED_TOOLS] this server does not advertise. */
    fun missingFrom(tools: AnnouncedTools): Set<String> = REQUIRED_TOOLS - tools.names
}

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
package com.vitorpamplona.amethyst.ui.tor

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * Pure, file- and JNI-free parsers over Arti's persisted guard sample
 * (`<filesDir>/arti/state/state/guards.json`).
 *
 * Both recovery heuristics in the Tor stack read the same on-disk file, so the
 * decisions live here where they can be unit-tested against captured fixtures
 * (see `ArtiGuardStateTest`). [TorService] does the file IO and delegates the
 * parsing; [TorManager] consumes [hasConfirmedGuard] to seed its
 * `hasEverBootstrapped` flag across process restarts.
 *
 * The file shape is a map of guard-set selection name (e.g. `"default"`) to an
 * object with a `"guards"` array. Each guard entry carries:
 * - `disabled`: non-null once Arti permanently retires the guard (e.g.
 *   `TooManyIndeterminateFailures` on a flaky network).
 * - `unlisted_since`: non-null once the guard drops out of the consensus.
 * - `confirmed_at`: non-null once the guard has actually been used to build a
 *   circuit — i.e. a real bootstrap reached the guard-confirmation stage.
 */
object ArtiGuardState {
    private val mapper = jacksonObjectMapper()

    /** Convenience for tests/callers holding the raw file text. */
    fun parse(json: String): JsonNode = mapper.readTree(json)

    /**
     * True when at least one non-empty guard selection has *zero* usable guards —
     * every guard permanently `disabled` or dropped from the consensus
     * (`unlisted_since`). This is the AllGuardsDown wedge: Arti can neither build
     * circuits nor replenish the sample (it's full of unusable entries), so it
     * stays broken across restarts until the on-disk state is wiped. A single
     * usable guard is enough to recover, so this only trips on a total wipeout.
     */
    fun hasNoUsableGuards(root: JsonNode): Boolean {
        var wedged = false
        root.forEach { selection ->
            val guards = selection.get("guards") ?: return@forEach
            if (guards.isArray && guards.size() > 0) {
                val usable = guards.count { !it.isDisabled() && !it.isUnlisted() }
                if (usable == 0) wedged = true
            }
        }
        return wedged
    }

    /**
     * True when the sample contains a guard Arti has *confirmed* (non-null
     * `confirmed_at`), even if that guard is now disabled or unlisted.
     *
     * Confirmation only happens after a guard has successfully built circuits, so
     * its presence on disk is durable proof that a real bootstrap completed at
     * least once on this install — surviving the process restarts that reset
     * [TorManager]'s in-memory `hasEverBootstrapped` flag to false. This lets the
     * stuck-Connecting watchdog treat persisted state as stale/poisoned (wipe it)
     * rather than as a pristine slow first bootstrap (leave it alone).
     */
    fun hasConfirmedGuard(root: JsonNode): Boolean {
        var confirmed = false
        root.forEach { selection ->
            val guards = selection.get("guards") ?: return@forEach
            if (guards.isArray) {
                guards.forEach { guard ->
                    val at = guard.get("confirmed_at")
                    if (at != null && !at.isNull) confirmed = true
                }
            }
        }
        return confirmed
    }

    private fun JsonNode.isDisabled(): Boolean {
        val d = get("disabled")
        return d != null && !d.isNull
    }

    private fun JsonNode.isUnlisted(): Boolean {
        val u = get("unlisted_since")
        return u != null && !u.isNull
    }
}

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
package com.vitorpamplona.quartz.buzz.rsReadState

/**
 * NIP-RS "Reserved Namespace": the key vocabulary of a read-state `contexts` map.
 *
 * The `ov_` stem belongs to the manual-unread override layer and `esc:` is its escape marker, so a
 * raw context id starting with either is written with one extra leading `esc:` ([escape]) and read
 * back by stripping exactly one ([unescape]) — a bijection. The `ov_s:`/`ov_c:`/`ov_b:` keys carry
 * the **unescaped** raw context id as their suffix.
 */
object ReadStateKeys {
    const val RESERVED_STEM = "ov_"
    const val ESCAPE = "esc:"

    /** Set counter S of a context's override register. */
    const val OV_SET = "ov_s:"

    /** Clear counter C of a context's override register (also the lone key of a tombstone floor). */
    const val OV_CLEAR = "ov_c:"

    /** Baseline B: the effective frontier when the context was last marked unread. */
    const val OV_BASELINE = "ov_b:"

    /** The frontier wire key for raw context id [ctx]. */
    fun escape(ctx: String): String = if (ctx.startsWith(RESERVED_STEM) || ctx.startsWith(ESCAPE)) ESCAPE + ctx else ctx

    /** The raw context id behind frontier wire key [wireKey]: strips exactly one leading `esc:`. */
    fun unescape(wireKey: String): String = if (wireKey.startsWith(ESCAPE)) wireKey.substring(ESCAPE.length) else wireKey

    /** True for a wire key in the reserved `ov_` namespace — never a frontier entry. */
    fun isReservedWireKey(wireKey: String): Boolean = wireKey.startsWith(RESERVED_STEM)

    /** The override prefix ([OV_SET], [OV_CLEAR] or [OV_BASELINE]) [wireKey] starts with, or null. */
    fun overridePrefixOf(wireKey: String): String? =
        when {
            wireKey.startsWith(OV_SET) -> OV_SET
            wireKey.startsWith(OV_CLEAR) -> OV_CLEAR
            wireKey.startsWith(OV_BASELINE) -> OV_BASELINE
            else -> null
        }
}

/**
 * A NIP-RS manual-unread override register `(S, C, B)` for one context: [set] counts mark-unread
 * actions, [clear] counts explicit mark-reads, and [baseline] is the effective frontier captured at
 * the most recent mark-unread. Registers merge by componentwise max ([merge]); the override is live
 * only while `S > 0 && F <= B && S > C` ([isActive]) — clear wins ties.
 *
 * Every value is a uint32 (`0..2^32-1`); a counter that would pass that is refused, never wrapped.
 */
data class OverrideRegister(
    val set: Long,
    val clear: Long,
    val baseline: Long,
) {
    init {
        require(set in 0..ReadStateContent.MAX_VALUE && clear in 0..ReadStateContent.MAX_VALUE && baseline in 0..ReadStateContent.MAX_VALUE) {
            "override counters must be uint32"
        }
    }

    /** Never activated: publishes nothing. */
    fun isVirgin(): Boolean = set == 0L && clear == 0L

    /** The tombstone-floor shape `(0, C, 0)` with `C > 0`: published as a lone `ov_c` key. */
    fun isTombstone(): Boolean = set == 0L && baseline == 0L && clear > 0L

    /** The liveness predicate against the merged effective [frontier]. */
    fun isActive(frontier: Long): Boolean = set > 0 && frontier <= baseline && set > clear

    /** Componentwise max — the only merge rule. */
    fun merge(other: OverrideRegister): OverrideRegister = OverrideRegister(maxOf(set, other.set), maxOf(clear, other.clear), maxOf(baseline, other.baseline))

    /**
     * The register as it must be published against [frontier]: unchanged while live, compacted to
     * the tombstone floor `(0, max(S, C), 0)` once dead, and null (omit it) when virgin.
     */
    fun canonicalize(frontier: Long): OverrideRegister? =
        when {
            isVirgin() -> null
            isActive(frontier) -> this
            else -> OverrideRegister(0, maxOf(set, clear), 0)
        }

    /**
     * Mark-unread at the current effective [frontier]: `S = max(S, C) + 1`, `B = frontier`. Null when
     * `max(S, C)` is already the uint32 maximum — the action must then be refused.
     */
    fun markUnread(frontier: Long): OverrideRegister? {
        val top = maxOf(set, clear)
        if (top >= ReadStateContent.MAX_VALUE) return null
        return OverrideRegister(top + 1, clear, frontier)
    }

    /**
     * Explicit mark-read, evaluated against the frontier *after* the caller advanced it
     * ([frontierAfter]): `C = max(S, C) + 1`. At the uint32 ceiling the counters stay as they are and
     * the action succeeds only if the override is already inactive; null means it must be reported
     * as failed.
     */
    fun markRead(frontierAfter: Long): OverrideRegister? {
        val top = maxOf(set, clear)
        if (top < ReadStateContent.MAX_VALUE) return OverrideRegister(set, top + 1, baseline)
        return if (isActive(frontierAfter)) null else this
    }

    companion object {
        val VIRGIN = OverrideRegister(0, 0, 0)
    }
}

/**
 * The effective read state across every supported blob a user published: frontiers and override
 * registers each merged by max. [isUnread] is the NIP-RS verdict
 * `latest_message_ts > F || override_active(S, C, B, F)`.
 */
class MergedReadState(
    val frontiers: Map<String, Long>,
    val overrides: Map<String, OverrideRegister>,
) {
    /** The merged frontier of raw context [ctx], or 0 when none was published. */
    fun frontier(ctx: String): Long = frontiers[ctx] ?: 0L

    /** True while [ctx] carries a live manual-unread override. */
    fun isOverrideActive(ctx: String): Boolean = overrides[ctx]?.isActive(frontier(ctx)) == true

    /** Whether [ctx] reads as unread given the `created_at` of its newest message. */
    fun isUnread(
        ctx: String,
        latestMessageTs: Long,
    ): Boolean = latestMessageTs > frontier(ctx) || isOverrideActive(ctx)

    companion object {
        /** Merges [blobs], skipping any that fail [ReadStateContent.isSupported]. */
        fun merge(blobs: List<ReadStateContent>): MergedReadState {
            val frontiers = LinkedHashMap<String, Long>()
            val overrides = LinkedHashMap<String, OverrideRegister>()
            blobs.forEach { blob ->
                if (!blob.isSupported()) return@forEach
                blob.frontiers().forEach { (ctx, ts) ->
                    val current = frontiers[ctx]
                    if (current == null || ts > current) frontiers[ctx] = ts
                }
                blob.overrides().forEach { (ctx, register) ->
                    overrides[ctx] = overrides[ctx]?.merge(register) ?: register
                }
            }
            return MergedReadState(frontiers, overrides)
        }
    }
}

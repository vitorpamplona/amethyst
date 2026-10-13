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
package com.vitorpamplona.quartz.nip85TrustedAssertions

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.TagArray

/**
 * One numeric claim on a trusted assertion (kinds 30382-30385), read without knowing its name:
 * `["rank", "63"]`, `["followers", "188"]`, `["personalizedGrapeRank_influence", "0.53"]`.
 *
 * The metric vocabulary is open: providers add their own counts and diagnostics beside the
 * names Quartz has typed accessors for, and a reader that shows "what this assertion says"
 * has to show those too. [value] is the string as published, so a decimal reads back exactly;
 * [number] is its parsed form for ordering and formatting.
 */
@Immutable
data class AssertionMetric(
    val name: String,
    val value: String,
    val number: Double,
) {
    /** True when the value is a whole number, so it can be printed without a decimal point. */
    fun isWhole() = value.isNotEmpty() && value.withIndex().all { (i, c) -> c.isDigit() || (i == 0 && c == '-') }
}

/**
 * Tags that are never metrics even when their value parses as a number: the protocol's
 * single-letter tags (the `d` subject, `p`/`e`/`a`/`k` pointers, `t` topics) are filtered by
 * length, these by name.
 */
private val NON_METRIC_TAGS = setOf("client", "alt", "expiration", "petname", "summary", "active_hours_start", "active_hours_end")

/** A plain decimal number: no exponent, no `d`/`f` suffix (which the JVM's parser accepts), no `NaN`. */
private val PLAIN_NUMBER = Regex("-?[0-9]+(\\.[0-9]+)?")

/** Timestamps are numbers but not metrics: `first_created_at`, `verified_at`, `first_seen`, `last_seen`. */
private fun isTimestampName(name: String) = name.endsWith("_at") || name.endsWith("_seen")

/**
 * Every numeric tag on an assertion, in tag order, the first occurrence of each name only.
 *
 * Mirrors Brainstorm's `assertionMetrics` (`lib/activity.ts`), so the two clients show the same
 * numbers for the same event. Only plain decimals count (`5d` is a duration, not five). Hours of
 * the day and timestamps are left out: they are numbers, but not counts or scores, and printing
 * `first_created_at` as "1,700,000,000" says nothing.
 */
fun TagArray.assertionMetrics(): List<AssertionMetric> {
    val out = ArrayList<AssertionMetric>()
    // A set, not a scan of [out]: the tags are the author's, and thousands of them must stay linear.
    val seen = HashSet<String>()
    for (tag in this) {
        if (tag.size < 2) continue
        val name = tag[0]
        if (name.length < 2 || name in NON_METRIC_TAGS || isTimestampName(name) || name in seen) continue
        val value = tag[1].trim()
        if (!PLAIN_NUMBER.matches(value)) continue
        val number = value.toDoubleOrNull() ?: continue
        seen.add(name)
        out.add(AssertionMetric(name, value, number))
    }
    return out
}

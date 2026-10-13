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
 * A moment a trusted assertion states among its counts: a provider's own timestamps
 * (`mint_audit_at`, `last_seen`, `checkedAt`) and NIP-40's `expiration`. [value] is the string as
 * published; [seconds] is it in unix seconds, a millisecond value divided down.
 */
@Immutable
data class AssertionTimestamp(
    val name: String,
    val value: String,
    val seconds: Long,
)

/**
 * Tags that are never metrics even when their value parses as a number: the protocol's
 * single-letter tags (the `d` subject, `p`/`e`/`a`/`k` pointers, `t` topics) are filtered by
 * length, these by name, and timestamps by [isAssertionTimeName].
 */
private val NON_METRIC_TAGS = setOf("client", "alt", "petname", "summary", "active_hours_start", "active_hours_end")

/** A plain decimal number: no exponent, no `d`/`f` suffix (which the JVM's parser accepts), no `NaN`. */
private val PLAIN_NUMBER = Regex("-?[0-9]+(\\.[0-9]+)?")

/** 2000-01-01 and 2100-01-01, in unix seconds: a moment outside them is not one a provider means. */
private const val Y2000 = 946_684_800L
private const val Y2100 = 4_102_444_800L

/**
 * True for a tag name that holds a moment rather than an amount. Brainstorm's rule
 * (`/(^expiration$|_at$|[a-z]At$|_time$|timestamp$)/`, case-sensitive, `lib/activity.ts`) plus the
 * `_seen` names (`first_seen`, `last_seen`) providers also publish.
 */
fun isAssertionTimeName(name: String): Boolean =
    name == "expiration" ||
        name.endsWith("_at") ||
        name.endsWith("_time") ||
        name.endsWith("timestamp") ||
        name.endsWith("_seen") ||
        (name.length > 2 && name.endsWith("At") && name[name.length - 3] in 'a'..'z')

/**
 * Every numeric tag on an assertion, in tag order, the first occurrence of each name only.
 *
 * Mirrors Brainstorm's `assertionMetrics` (`lib/activity.ts`), so the two clients show the same
 * numbers for the same event. Only plain decimals count (`5d` is a duration, not five). Hours of
 * the day and timestamps are left out: they are numbers, but not counts or scores, and printing
 * `first_created_at` as "1,700,000,000" says nothing. [assertionTimestamps] reads the timestamps.
 */
fun TagArray.assertionMetrics(): List<AssertionMetric> {
    val out = ArrayList<AssertionMetric>()
    // A set, not a scan of [out]: the tags are the author's, and thousands of them must stay linear.
    val seen = HashSet<String>()
    for (tag in this) {
        if (tag.size < 2) continue
        val name = tag[0]
        if (name.length < 2 || name in NON_METRIC_TAGS || isAssertionTimeName(name) || name in seen) continue
        val value = tag[1].trim()
        if (!PLAIN_NUMBER.matches(value)) continue
        val number = value.toDoubleOrNull() ?: continue
        seen.add(name)
        out.add(AssertionMetric(name, value, number))
    }
    return out
}

/**
 * Every timestamp on an assertion ([isAssertionTimeName]), in tag order, the first occurrence of
 * each name only: the moments a card shows as dates, after its counts. A value is a moment only
 * when it is a unix time between 2000 and 2100, seconds or milliseconds; anything else under a time
 * name (a `seen_at` of 3) is dropped rather than shown as a number.
 */
fun TagArray.assertionTimestamps(): List<AssertionTimestamp> {
    val out = ArrayList<AssertionTimestamp>()
    val seen = HashSet<String>()
    for (tag in this) {
        if (tag.size < 2) continue
        val name = tag[0]
        if (!isAssertionTimeName(name) || name in seen) continue
        val value = tag[1].trim()
        if (!PLAIN_NUMBER.matches(value)) continue
        val number = value.toDoubleOrNull() ?: continue
        seen.add(name)
        val seconds = (if (number >= Y2000 * 1000) number / 1000 else number).toLong()
        if (seconds in Y2000 until Y2100) out.add(AssertionTimestamp(name, value, seconds))
    }
    return out
}

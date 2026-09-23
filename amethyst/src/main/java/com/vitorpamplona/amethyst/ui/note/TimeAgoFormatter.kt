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
package com.vitorpamplona.amethyst.ui.note

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.d
import com.vitorpamplona.amethyst.commons.resources.duration_days
import com.vitorpamplona.amethyst.commons.resources.duration_hours
import com.vitorpamplona.amethyst.commons.resources.duration_minutes
import com.vitorpamplona.amethyst.commons.resources.duration_months
import com.vitorpamplona.amethyst.commons.resources.duration_weeks
import com.vitorpamplona.amethyst.commons.resources.duration_years
import com.vitorpamplona.amethyst.commons.resources.h
import com.vitorpamplona.amethyst.commons.resources.last_seen
import com.vitorpamplona.amethyst.commons.resources.last_seen_just_now
import com.vitorpamplona.amethyst.commons.resources.last_seen_never
import com.vitorpamplona.amethyst.commons.resources.last_seen_on_date
import com.vitorpamplona.amethyst.commons.resources.m
import com.vitorpamplona.amethyst.commons.resources.never
import com.vitorpamplona.amethyst.commons.resources.now
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.utils.TimeUtils
import org.jetbrains.compose.resources.StringResource
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.round

// Skeletons follow Unicode LDML — DateFormat.getBestDateTimePattern picks the
// correct locale-specific ordering (e.g. "MMM d, y" in en-US vs "d MMM y" in en-GB).
private const val YEAR_SKELETON = "yMMMd"
private const val MONTH_SKELETON = "MMMd"
private const val YEAR_NO_DAY_SKELETON = "yMMM"

/**
 * Per-thread cached [SimpleDateFormat] keyed off the current default [Locale].
 *
 * `SimpleDateFormat` is mutable and not thread-safe, and these formatters are
 * read from both the UI thread (composition) and background coroutines
 * (e.g. `LocalCache.justVerify` logging failed event verifications). A bare
 * `var` shared across threads would race on the formatter's internal Calendar.
 * Using `ThreadLocal` gives each thread its own instance — no locks, no
 * allocation per call, and we rebuild lazily on locale change.
 */
private class LocaleAwareFormatter(
    private val skeleton: String,
) {
    private val cache = ThreadLocal<Pair<Locale, SimpleDateFormat>>()

    fun get(): SimpleDateFormat {
        val current = Locale.getDefault()
        val cached = cache.get()
        if (cached != null && cached.first == current) return cached.second
        val fresh = SimpleDateFormat(DateFormat.getBestDateTimePattern(current, skeleton), current)
        cache.set(current to fresh)
        return fresh
    }
}

private val yearFormatter = LocaleAwareFormatter(YEAR_SKELETON)
private val monthFormatter = LocaleAwareFormatter(MONTH_SKELETON)
private val yearNoDayFormatter = LocaleAwareFormatter(YEAR_NO_DAY_SKELETON)

/**
 * The handful of unit labels the relative formatters splice into their output.
 *
 * Resolved once in composition so the formatters themselves can stay ordinary
 * functions. [com.vitorpamplona.amethyst.ui.note.elements.TimeAgo] builds its text
 * inside a `derivedStateOf`, which is not a composable scope, so a @Composable
 * formatter could not be called from there at all.
 */
@Immutable
class TimeAgoLabels(
    val never: String,
    val now: String,
    val minutes: String,
    val hours: String,
    val days: String,
)

@Composable
fun rememberTimeAgoLabels(
    seconds: StringResource = Res.string.now,
    minutes: StringResource = Res.string.m,
    hours: StringResource = Res.string.h,
    days: StringResource = Res.string.d,
): TimeAgoLabels {
    val neverStr = stringRes(Res.string.never)
    val nowStr = stringRes(seconds)
    val minStr = stringRes(minutes)
    val hourStr = stringRes(hours)
    val dayStr = stringRes(days)
    return remember(neverStr, nowStr, minStr, hourStr, dayStr) {
        TimeAgoLabels(neverStr, nowStr, minStr, hourStr, dayStr)
    }
}

/** Plain-function core of [timeAgo], callable outside composition. */
fun timeAgoWith(
    time: Long?,
    labels: TimeAgoLabels,
    prefix: String = " • ",
): String {
    if (time == null) return " "
    if (time == 0L) return prefix + labels.never

    val timeDifference = TimeUtils.now() - time

    return when {
        timeDifference > TimeUtils.ONE_YEAR -> prefix + yearFormatter.get().format(time * 1000)
        timeDifference > TimeUtils.ONE_MONTH -> prefix + monthFormatter.get().format(time * 1000)
        timeDifference > TimeUtils.ONE_DAY -> prefix + (timeDifference / TimeUtils.ONE_DAY).toString() + labels.days
        timeDifference > TimeUtils.ONE_HOUR -> prefix + (timeDifference / TimeUtils.ONE_HOUR).toString() + labels.hours
        timeDifference > TimeUtils.ONE_MINUTE -> prefix + (timeDifference / TimeUtils.ONE_MINUTE).toString() + labels.minutes
        else -> prefix + labels.now
    }
}

/** Plain-function core of [timeAbsolute], callable outside composition. */
fun timeAbsoluteWith(
    time: Long?,
    context: Context,
    never: String,
    prefix: String = " • ",
): String {
    if (time == null) return " "
    if (time == 0L) return prefix + never

    val timeMs = time * 1000
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timeMs }

    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val sameDay = sameYear && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)

    val timeOfDay = DateFormat.getTimeFormat(context).format(Date(timeMs))

    return when {
        sameDay -> prefix + timeOfDay
        sameYear -> prefix + monthFormatter.get().format(timeMs) + ", " + timeOfDay
        else -> prefix + yearFormatter.get().format(timeMs)
    }
}

/**
 * Formats a Unix timestamp (seconds) as an absolute date/time string, picking the
 * granularity from how far away the timestamp is:
 *   - same day → time only (locale + system 12/24-hr aware via [DateFormat.getTimeFormat])
 *   - same year → "Jan 5, 14:32" / "5 Jan 14:32" / "Jan 5, 2:32 PM" (locale + system aware)
 *   - older    → "Jan 5, 2024" / "5 Jan 2024" (locale aware)
 *
 * Used by [com.vitorpamplona.amethyst.ui.note.elements.TimeAgo] when the user
 * taps the relative timestamp to reveal the absolute one.
 */
@Composable
fun timeAbsolute(
    time: Long?,
    context: Context,
    prefix: String = " • ",
): String = timeAbsoluteWith(time, context, stringRes(Res.string.never), prefix)

@Composable
fun timeAbsoluteNoDot(
    time: Long?,
    context: Context,
): String = timeAbsolute(time, context, prefix = "")

@Composable
fun timeAgo(
    time: Long?,
    prefix: String = " • ",
    seconds: StringResource = Res.string.now,
    minutes: StringResource = Res.string.m,
    hours: StringResource = Res.string.h,
    days: StringResource = Res.string.d,
): String = timeAgoWith(time, rememberTimeAgoLabels(seconds, minutes, hours, days), prefix)

@Composable
fun timeAgoNoDot(time: Long?): String {
    if (time == null) return " "
    if (time == 0L) return " ${stringRes(Res.string.never)}"

    val timeDifference = TimeUtils.now() - time

    return when {
        timeDifference > TimeUtils.ONE_YEAR -> {
            yearFormatter.get().format(time * 1000)
        }

        timeDifference > TimeUtils.ONE_MONTH -> {
            monthFormatter.get().format(time * 1000)
        }

        timeDifference > TimeUtils.ONE_DAY -> {
            (timeDifference / TimeUtils.ONE_DAY).toString() + stringRes(Res.string.d)
        }

        timeDifference > TimeUtils.ONE_HOUR -> {
            (timeDifference / TimeUtils.ONE_HOUR).toString() + stringRes(Res.string.h)
        }

        timeDifference > TimeUtils.ONE_MINUTE -> {
            (timeDifference / TimeUtils.ONE_MINUTE).toString() + stringRes(Res.string.m)
        }

        else -> {
            stringRes(Res.string.now)
        }
    }
}

@Composable
fun timeAgoNoDotNoDay(time: Long?): String {
    if (time == null) return " "
    if (time == 0L) return " ${stringRes(Res.string.never)}"

    val timeDifference = TimeUtils.now() - time

    return when {
        timeDifference > TimeUtils.ONE_YEAR -> {
            yearNoDayFormatter.get().format(time * 1000)
        }

        timeDifference > TimeUtils.ONE_MONTH -> {
            monthFormatter.get().format(time * 1000)
        }

        timeDifference > TimeUtils.ONE_DAY -> {
            (timeDifference / TimeUtils.ONE_DAY).toString() + stringRes(Res.string.d)
        }

        timeDifference > TimeUtils.ONE_HOUR -> {
            (timeDifference / TimeUtils.ONE_HOUR).toString() + stringRes(Res.string.h)
        }

        timeDifference > TimeUtils.ONE_MINUTE -> {
            (timeDifference / TimeUtils.ONE_MINUTE).toString() + stringRes(Res.string.m)
        }

        else -> {
            stringRes(Res.string.now)
        }
    }
}

@Composable
fun timeAheadNoDot(time: Long?): String {
    if (time == null) return " "
    if (time == 0L) return " ${stringRes(Res.string.never)}"

    val timeDifference = time - TimeUtils.now()

    return when {
        timeDifference > TimeUtils.ONE_YEAR -> {
            yearFormatter.get().format(time * 1000)
        }

        timeDifference > TimeUtils.ONE_MONTH -> {
            monthFormatter.get().format(time * 1000)
        }

        timeDifference > TimeUtils.ONE_DAY -> {
            round(timeDifference / TimeUtils.ONE_DAY.toFloat()).toInt().toString() + stringRes(Res.string.d)
        }

        timeDifference > TimeUtils.ONE_HOUR -> {
            round(timeDifference / TimeUtils.ONE_HOUR.toFloat()).toInt().toString() + stringRes(Res.string.h)
        }

        timeDifference > TimeUtils.ONE_MINUTE -> {
            round(timeDifference / TimeUtils.ONE_MINUTE.toFloat()).toInt().toString() + stringRes(Res.string.m)
        }

        else -> {
            stringRes(Res.string.now)
        }
    }
}

fun dateFormatter(
    time: Long?,
    never: String,
    today: String,
): String {
    if (time == null) return " "
    if (time == 0L) return " $never"

    val timeDifference = TimeUtils.now() - time

    return if (timeDifference > TimeUtils.ONE_YEAR) {
        yearFormatter.get().format(time * 1000)
    } else if (timeDifference > TimeUtils.ONE_DAY) {
        monthFormatter.get().format(time * 1000)
    } else {
        today
    }
}

/**
 * Builds the full "Last seen ..." sentence shown on profiles and similar UI.
 *
 * Unlike [timeAgo], which can return a bare absolute date string (e.g. "Jan 14")
 * for older timestamps, this function always returns a self-contained, grammatical
 * description such as:
 *
 *   - "Last seen 5 minutes ago"
 *   - "Last seen 2 hours ago"
 *   - "Last seen 3 days ago"
 *   - "Last seen 2 weeks ago"
 *   - "Last seen on Jul 27, 2024 (9 months ago)"
 *   - "Last seen on Jan 14, 2024 (1 year ago)"
 *
 * The duration component uses sensible units (seconds/minutes/hours/days/weeks/months/years)
 * and pluralizes via Android plural resources. For anything older than a week we also
 * include the absolute date so users see exactly when the activity happened.
 */
@Composable
fun lastSeenSentence(time: Long?): String {
    if (time == null) return ""
    if (time == 0L) return stringRes(Res.string.last_seen_never)

    val nowSec = TimeUtils.now()
    val diff = nowSec - time

    // Negative drift (clock skew, future timestamp) — treat as "just now".
    if (diff < TimeUtils.ONE_MINUTE) {
        return stringRes(Res.string.last_seen_just_now)
    }

    // Recent: render purely as a relative duration.
    if (diff < TimeUtils.ONE_WEEK) {
        val durationText =
            when {
                diff < TimeUtils.ONE_HOUR -> {
                    val n = (diff / TimeUtils.ONE_MINUTE).toInt()
                    pluralStringRes(Res.plurals.duration_minutes, n, n)
                }

                diff < TimeUtils.ONE_DAY -> {
                    val n = (diff / TimeUtils.ONE_HOUR).toInt()
                    pluralStringRes(Res.plurals.duration_hours, n, n)
                }

                else -> {
                    val n = (diff / TimeUtils.ONE_DAY).toInt()
                    pluralStringRes(Res.plurals.duration_days, n, n)
                }
            }
        return stringRes(Res.string.last_seen, durationText)
    }

    // Older than a week: include absolute date plus a coarse relative duration.
    val durationText =
        when {
            diff < TimeUtils.ONE_MONTH -> {
                val n = (diff / TimeUtils.ONE_WEEK).toInt()
                pluralStringRes(Res.plurals.duration_weeks, n, n)
            }

            diff < TimeUtils.ONE_YEAR -> {
                val n = (diff / TimeUtils.ONE_MONTH).toInt().coerceAtLeast(1)
                pluralStringRes(Res.plurals.duration_months, n, n)
            }

            else -> {
                val n = (diff / TimeUtils.ONE_YEAR).toInt().coerceAtLeast(1)
                pluralStringRes(Res.plurals.duration_years, n, n)
            }
        }

    val dateText = yearFormatter.get().format(time * 1000)

    return stringRes(Res.string.last_seen_on_date, dateText, durationText)
}

fun timeAgoShort(
    mills: Long?,
    stringForNow: String,
): String {
    if (mills == null) return " "

    var humanReadable =
        DateUtils
            .getRelativeTimeSpanString(
                mills * 1000,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_ALL,
            ).toString()
    if (humanReadable.startsWith("In") || humanReadable.startsWith("0")) {
        humanReadable = stringForNow
    }

    return humanReadable
}

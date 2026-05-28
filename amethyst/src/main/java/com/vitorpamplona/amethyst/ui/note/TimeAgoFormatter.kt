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
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.utils.TimeUtils
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

private var locale: Locale = Locale.getDefault()
private var yearFormatter = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, YEAR_SKELETON), locale)
private var monthFormatter = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, MONTH_SKELETON), locale)
private var yearNoDayFormatter = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, YEAR_NO_DAY_SKELETON), locale)

private fun updateFormattersIfNeeded() {
    val current = Locale.getDefault()
    if (locale != current) {
        locale = current
        yearFormatter = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, YEAR_SKELETON), locale)
        monthFormatter = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, MONTH_SKELETON), locale)
        yearNoDayFormatter = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, YEAR_NO_DAY_SKELETON), locale)
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
fun timeAbsolute(
    time: Long?,
    context: Context,
    prefix: String = " • ",
): String {
    if (time == null) return " "
    if (time == 0L) return prefix + stringRes(context, R.string.never)

    updateFormattersIfNeeded()

    val timeMs = time * 1000
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timeMs }

    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val sameDay = sameYear && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)

    val timeOfDay = DateFormat.getTimeFormat(context).format(Date(timeMs))

    return when {
        sameDay -> prefix + timeOfDay
        sameYear -> prefix + monthFormatter.format(timeMs) + ", " + timeOfDay
        else -> prefix + yearFormatter.format(timeMs)
    }
}

fun timeAbsoluteNoDot(
    time: Long?,
    context: Context,
): String = timeAbsolute(time, context, prefix = "")

fun timeAgo(
    time: Long?,
    context: Context,
    prefix: String = " • ",
    seconds: Int = R.string.now,
    minutes: Int = R.string.m,
    hours: Int = R.string.h,
    days: Int = R.string.d,
): String {
    if (time == null) return " "
    if (time == 0L) return prefix + stringRes(context, R.string.never)

    val timeDifference = TimeUtils.now() - time

    return when {
        timeDifference > TimeUtils.ONE_YEAR -> {
            updateFormattersIfNeeded()
            prefix + yearFormatter.format(time * 1000)
        }

        timeDifference > TimeUtils.ONE_MONTH -> {
            updateFormattersIfNeeded()
            prefix + monthFormatter.format(time * 1000)
        }

        timeDifference > TimeUtils.ONE_DAY -> {
            prefix + (timeDifference / TimeUtils.ONE_DAY).toString() + stringRes(context, days)
        }

        timeDifference > TimeUtils.ONE_HOUR -> {
            prefix + (timeDifference / TimeUtils.ONE_HOUR).toString() + stringRes(context, hours)
        }

        timeDifference > TimeUtils.ONE_MINUTE -> {
            prefix + (timeDifference / TimeUtils.ONE_MINUTE).toString() + stringRes(context, minutes)
        }

        else -> {
            prefix + stringRes(context, seconds)
        }
    }
}

fun timeAgoNoDot(
    time: Long?,
    context: Context,
): String {
    if (time == null) return " "
    if (time == 0L) return " ${stringRes(context, R.string.never)}"

    val timeDifference = TimeUtils.now() - time

    return when {
        timeDifference > TimeUtils.ONE_YEAR -> {
            updateFormattersIfNeeded()
            yearFormatter.format(time * 1000)
        }

        timeDifference > TimeUtils.ONE_MONTH -> {
            updateFormattersIfNeeded()
            monthFormatter.format(time * 1000)
        }

        timeDifference > TimeUtils.ONE_DAY -> {
            (timeDifference / TimeUtils.ONE_DAY).toString() + stringRes(context, R.string.d)
        }

        timeDifference > TimeUtils.ONE_HOUR -> {
            (timeDifference / TimeUtils.ONE_HOUR).toString() + stringRes(context, R.string.h)
        }

        timeDifference > TimeUtils.ONE_MINUTE -> {
            (timeDifference / TimeUtils.ONE_MINUTE).toString() + stringRes(context, R.string.m)
        }

        else -> {
            stringRes(context, R.string.now)
        }
    }
}

fun timeAgoNoDotNoDay(
    time: Long?,
    context: Context,
): String {
    if (time == null) return " "
    if (time == 0L) return " ${stringRes(context, R.string.never)}"

    val timeDifference = TimeUtils.now() - time

    return when {
        timeDifference > TimeUtils.ONE_YEAR -> {
            updateFormattersIfNeeded()
            yearNoDayFormatter.format(time * 1000)
        }

        timeDifference > TimeUtils.ONE_MONTH -> {
            updateFormattersIfNeeded()
            monthFormatter.format(time * 1000)
        }

        timeDifference > TimeUtils.ONE_DAY -> {
            (timeDifference / TimeUtils.ONE_DAY).toString() + stringRes(context, R.string.d)
        }

        timeDifference > TimeUtils.ONE_HOUR -> {
            (timeDifference / TimeUtils.ONE_HOUR).toString() + stringRes(context, R.string.h)
        }

        timeDifference > TimeUtils.ONE_MINUTE -> {
            (timeDifference / TimeUtils.ONE_MINUTE).toString() + stringRes(context, R.string.m)
        }

        else -> {
            stringRes(context, R.string.now)
        }
    }
}

fun timeAheadNoDot(
    time: Long?,
    context: Context,
): String {
    if (time == null) return " "
    if (time == 0L) return " ${stringRes(context, R.string.never)}"

    val timeDifference = time - TimeUtils.now()

    return when {
        timeDifference > TimeUtils.ONE_YEAR -> {
            updateFormattersIfNeeded()
            yearFormatter.format(time * 1000)
        }

        timeDifference > TimeUtils.ONE_MONTH -> {
            updateFormattersIfNeeded()
            monthFormatter.format(time * 1000)
        }

        timeDifference > TimeUtils.ONE_DAY -> {
            round(timeDifference / TimeUtils.ONE_DAY.toFloat()).toInt().toString() + stringRes(context, R.string.d)
        }

        timeDifference > TimeUtils.ONE_HOUR -> {
            round(timeDifference / TimeUtils.ONE_HOUR.toFloat()).toInt().toString() + stringRes(context, R.string.h)
        }

        timeDifference > TimeUtils.ONE_MINUTE -> {
            round(timeDifference / TimeUtils.ONE_MINUTE.toFloat()).toInt().toString() + stringRes(context, R.string.m)
        }

        else -> {
            stringRes(context, R.string.now)
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
        updateFormattersIfNeeded()
        yearFormatter.format(time * 1000)
    } else if (timeDifference > TimeUtils.ONE_DAY) {
        updateFormattersIfNeeded()
        monthFormatter.format(time * 1000)
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
fun lastSeenSentence(
    time: Long?,
    context: Context,
): String {
    if (time == null) return ""
    if (time == 0L) return stringRes(context, R.string.last_seen_never)

    val nowSec = TimeUtils.now()
    val diff = nowSec - time

    // Negative drift (clock skew, future timestamp) — treat as "just now".
    if (diff < TimeUtils.ONE_MINUTE) {
        return stringRes(context, R.string.last_seen_just_now)
    }

    val resources = context.resources

    // Recent: render purely as a relative duration.
    if (diff < TimeUtils.ONE_WEEK) {
        val durationText =
            when {
                diff < TimeUtils.ONE_HOUR -> {
                    val n = (diff / TimeUtils.ONE_MINUTE).toInt()
                    resources.getQuantityString(R.plurals.duration_minutes, n, n)
                }

                diff < TimeUtils.ONE_DAY -> {
                    val n = (diff / TimeUtils.ONE_HOUR).toInt()
                    resources.getQuantityString(R.plurals.duration_hours, n, n)
                }

                else -> {
                    val n = (diff / TimeUtils.ONE_DAY).toInt()
                    resources.getQuantityString(R.plurals.duration_days, n, n)
                }
            }
        return stringRes(context, R.string.last_seen, durationText)
    }

    // Older than a week: include absolute date plus a coarse relative duration.
    val durationText =
        when {
            diff < TimeUtils.ONE_MONTH -> {
                val n = (diff / TimeUtils.ONE_WEEK).toInt()
                resources.getQuantityString(R.plurals.duration_weeks, n, n)
            }

            diff < TimeUtils.ONE_YEAR -> {
                val n = (diff / TimeUtils.ONE_MONTH).toInt().coerceAtLeast(1)
                resources.getQuantityString(R.plurals.duration_months, n, n)
            }

            else -> {
                val n = (diff / TimeUtils.ONE_YEAR).toInt().coerceAtLeast(1)
                resources.getQuantityString(R.plurals.duration_years, n, n)
            }
        }

    updateFormattersIfNeeded()
    val dateText = yearFormatter.format(time * 1000)

    return stringRes(context, R.string.last_seen_on_date, dateText, durationText)
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

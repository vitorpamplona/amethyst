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
import android.text.format.DateUtils
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.utils.TimeUtils
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.round

private const val YEAR_DATE_FORMAT = "MMM dd, yyyy"
private const val MONTH_DATE_FORMAT = "MMM dd"

private const val YEAR_NO_DAY_DATE_FORMAT = "MMM yyyy"
private const val MONTH_NO_DAY_DATE_FORMAT = "MMM dd"

var locale: Locale = Locale.getDefault()
var yearFormatter = SimpleDateFormat(YEAR_DATE_FORMAT, locale)
var monthFormatter = SimpleDateFormat(MONTH_DATE_FORMAT, locale)

var yearNoDayFormatter = SimpleDateFormat(YEAR_NO_DAY_DATE_FORMAT, locale)
var monthNoDayFormatter = SimpleDateFormat(MONTH_NO_DAY_DATE_FORMAT, locale)

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

    return if (timeDifference > TimeUtils.ONE_YEAR) {
        // Dec 12, 2022

        if (locale != Locale.getDefault()) {
            locale = Locale.getDefault()
            yearFormatter = SimpleDateFormat(YEAR_DATE_FORMAT, locale)
            monthFormatter = SimpleDateFormat(MONTH_DATE_FORMAT, locale)
        }

        prefix + yearFormatter.format(time * 1000)
    } else if (timeDifference > TimeUtils.ONE_MONTH) {
        // Dec 12
        if (locale != Locale.getDefault()) {
            locale = Locale.getDefault()
            yearFormatter = SimpleDateFormat(YEAR_DATE_FORMAT, locale)
            monthFormatter = SimpleDateFormat(MONTH_DATE_FORMAT, locale)
        }

        prefix + monthFormatter.format(time * 1000)
    } else if (timeDifference > TimeUtils.ONE_DAY) {
        // 2 days
        prefix + (timeDifference / TimeUtils.ONE_DAY).toString() + stringRes(context, days)
    } else if (timeDifference > TimeUtils.ONE_HOUR) {
        prefix + (timeDifference / TimeUtils.ONE_HOUR).toString() + stringRes(context, hours)
    } else if (timeDifference > TimeUtils.ONE_MINUTE) {
        prefix + (timeDifference / TimeUtils.ONE_MINUTE).toString() + stringRes(context, minutes)
    } else {
        prefix + stringRes(context, seconds)
    }
}

fun timeAgoNoDot(
    time: Long?,
    context: Context,
): String {
    if (time == null) return " "
    if (time == 0L) return " ${stringRes(context, R.string.never)}"

    val timeDifference = TimeUtils.now() - time

    return if (timeDifference > TimeUtils.ONE_YEAR) {
        // Dec 12, 2022

        if (locale != Locale.getDefault()) {
            locale = Locale.getDefault()
            yearFormatter = SimpleDateFormat(YEAR_DATE_FORMAT, locale)
            monthFormatter = SimpleDateFormat(MONTH_DATE_FORMAT, locale)
        }

        yearFormatter.format(time * 1000)
    } else if (timeDifference > TimeUtils.ONE_MONTH) {
        // Dec 12
        if (locale != Locale.getDefault()) {
            locale = Locale.getDefault()
            yearFormatter = SimpleDateFormat(YEAR_DATE_FORMAT, locale)
            monthFormatter = SimpleDateFormat(MONTH_DATE_FORMAT, locale)
        }

        monthFormatter.format(time * 1000)
    } else if (timeDifference > TimeUtils.ONE_DAY) {
        // 2 days
        (timeDifference / TimeUtils.ONE_DAY).toString() + stringRes(context, R.string.d)
    } else if (timeDifference > TimeUtils.ONE_HOUR) {
        (timeDifference / TimeUtils.ONE_HOUR).toString() + stringRes(context, R.string.h)
    } else if (timeDifference > TimeUtils.ONE_MINUTE) {
        (timeDifference / TimeUtils.ONE_MINUTE).toString() + stringRes(context, R.string.m)
    } else {
        stringRes(context, R.string.now)
    }
}

fun timeAgoNoDotNoDay(
    time: Long?,
    context: Context,
): String {
    if (time == null) return " "
    if (time == 0L) return " ${stringRes(context, R.string.never)}"

    val timeDifference = TimeUtils.now() - time

    return if (timeDifference > TimeUtils.ONE_YEAR) {
        // Dec 12, 2022

        if (locale != Locale.getDefault()) {
            locale = Locale.getDefault()
            yearNoDayFormatter = SimpleDateFormat(YEAR_NO_DAY_DATE_FORMAT, locale)
            monthNoDayFormatter = SimpleDateFormat(MONTH_NO_DAY_DATE_FORMAT, locale)
        }

        yearNoDayFormatter.format(time * 1000)
    } else if (timeDifference > TimeUtils.ONE_MONTH) {
        // Dec 12
        if (locale != Locale.getDefault()) {
            locale = Locale.getDefault()
            yearNoDayFormatter = SimpleDateFormat(YEAR_NO_DAY_DATE_FORMAT, locale)
            monthNoDayFormatter = SimpleDateFormat(MONTH_NO_DAY_DATE_FORMAT, locale)
        }

        monthNoDayFormatter.format(time * 1000)
    } else if (timeDifference > TimeUtils.ONE_DAY) {
        // 2 days
        (timeDifference / TimeUtils.ONE_DAY).toString() + stringRes(context, R.string.d)
    } else if (timeDifference > TimeUtils.ONE_HOUR) {
        (timeDifference / TimeUtils.ONE_HOUR).toString() + stringRes(context, R.string.h)
    } else if (timeDifference > TimeUtils.ONE_MINUTE) {
        (timeDifference / TimeUtils.ONE_MINUTE).toString() + stringRes(context, R.string.m)
    } else {
        stringRes(context, R.string.now)
    }
}

fun timeAheadNoDot(
    time: Long?,
    context: Context,
): String {
    if (time == null) return " "
    if (time == 0L) return " ${stringRes(context, R.string.never)}"

    val timeDifference = time - TimeUtils.now()

    return if (timeDifference > TimeUtils.ONE_YEAR) {
        // Dec 12, 2022

        if (locale != Locale.getDefault()) {
            locale = Locale.getDefault()
            yearFormatter = SimpleDateFormat(YEAR_DATE_FORMAT, locale)
            monthFormatter = SimpleDateFormat(MONTH_DATE_FORMAT, locale)
        }

        yearFormatter.format(time * 1000)
    } else if (timeDifference > TimeUtils.ONE_MONTH) {
        // Dec 12
        if (locale != Locale.getDefault()) {
            locale = Locale.getDefault()
            yearFormatter = SimpleDateFormat(YEAR_DATE_FORMAT, locale)
            monthFormatter = SimpleDateFormat(MONTH_DATE_FORMAT, locale)
        }

        monthFormatter.format(time * 1000)
    } else if (timeDifference > TimeUtils.ONE_DAY) {
        // 2 days
        round(timeDifference / TimeUtils.ONE_DAY.toFloat()).toInt().toString() + stringRes(context, R.string.d)
    } else if (timeDifference > TimeUtils.ONE_HOUR) {
        round(timeDifference / TimeUtils.ONE_HOUR.toFloat()).toInt().toString() + stringRes(context, R.string.h)
    } else if (timeDifference > TimeUtils.ONE_MINUTE) {
        round(timeDifference / TimeUtils.ONE_MINUTE.toFloat()).toInt().toString() + stringRes(context, R.string.m)
    } else {
        stringRes(context, R.string.now)
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
        // Dec 12, 2022

        if (locale != Locale.getDefault()) {
            locale = Locale.getDefault()
            yearFormatter = SimpleDateFormat(YEAR_DATE_FORMAT, locale)
            monthFormatter = SimpleDateFormat(MONTH_DATE_FORMAT, locale)
        }

        yearFormatter.format(time * 1000)
    } else if (timeDifference > TimeUtils.ONE_DAY) {
        // Dec 12
        if (locale != Locale.getDefault()) {
            locale = Locale.getDefault()
            yearFormatter = SimpleDateFormat(YEAR_DATE_FORMAT, locale)
            monthFormatter = SimpleDateFormat(MONTH_DATE_FORMAT, locale)
        }

        monthFormatter.format(time * 1000)
    } else {
        today
    }
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

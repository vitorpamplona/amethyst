package com.vitorpamplona.amethyst.ui.note

import android.content.Context
import android.text.format.DateUtils
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.quartz.utils.TimeUtils
import java.text.SimpleDateFormat
import java.util.Locale

var locale = Locale.getDefault()
var yearFormatter = SimpleDateFormat(" • MMM dd, yyyy", locale)
var monthFormatter = SimpleDateFormat(" • MMM dd", locale)

fun timeAgo(time: Long?, context: Context): String {
    if (time == null) return " "
    if (time == 0L) return " • ${context.getString(R.string.never)}"

    val timeDifference = TimeUtils.now() - time

    return if (timeDifference > TimeUtils.oneYear) {
        // Dec 12, 2022

        if (locale != Locale.getDefault()) {
            locale = Locale.getDefault()
            yearFormatter = SimpleDateFormat(" • MMM dd, yyyy", locale)
            monthFormatter = SimpleDateFormat(" • MMM dd", locale)
        }

        yearFormatter.format(time * 1000)
    } else if (timeDifference > TimeUtils.oneMonth) {
        // Dec 12
        if (locale != Locale.getDefault()) {
            locale = Locale.getDefault()
            yearFormatter = SimpleDateFormat(" • MMM dd, yyyy", locale)
            monthFormatter = SimpleDateFormat(" • MMM dd", locale)
        }

        monthFormatter.format(time * 1000)
    } else if (timeDifference > TimeUtils.oneDay) {
        // 2 days
        " • " + (timeDifference / TimeUtils.oneDay).toString() + context.getString(R.string.d)
    } else if (timeDifference > TimeUtils.oneHour) {
        " • " + (timeDifference / TimeUtils.oneHour).toString() + context.getString(R.string.h)
    } else if (timeDifference > TimeUtils.oneMinute) {
        " • " + (timeDifference / TimeUtils.oneMinute).toString() + context.getString(R.string.m)
    } else {
        " • " + context.getString(R.string.now)
    }
}

fun timeAgoShort(mills: Long?, stringForNow: String): String {
    if (mills == null) return " "

    var humanReadable = DateUtils.getRelativeTimeSpanString(
        mills * 1000,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_ALL
    ).toString()
    if (humanReadable.startsWith("In") || humanReadable.startsWith("0")) {
        humanReadable = stringForNow
    }

    return humanReadable
}

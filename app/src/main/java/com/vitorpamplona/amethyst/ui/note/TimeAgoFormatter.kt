package com.vitorpamplona.amethyst.ui.note

import android.text.format.DateUtils

fun timeAgo(mills: Long?): String {
  if (mills == null) return " "

  var humanReadable = DateUtils.getRelativeTimeSpanString(
    mills * 1000,
    System.currentTimeMillis(),
    DateUtils.MINUTE_IN_MILLIS,
    DateUtils.FORMAT_ABBREV_ALL
  ).toString()
  if (humanReadable.startsWith("In") || humanReadable.startsWith("0")) {
    humanReadable = "now";
  }
  return humanReadable
}
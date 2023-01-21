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

  return " • " + humanReadable
    .replace(" hr. ago", "h")
    .replace(" min. ago", "m")
    .replace(" days ago", "d")
}

fun timeAgoLong(mills: Long?): String {
  if (mills == null) return " "

  var humanReadable = DateUtils.getRelativeTimeSpanString(
    mills * 1000,
    System.currentTimeMillis(),
    DateUtils.MINUTE_IN_MILLIS,
    DateUtils.FORMAT_SHOW_TIME
  ).toString()
  if (humanReadable.startsWith("In") || humanReadable.startsWith("0")) {
    humanReadable = "now";
  }

  return humanReadable
}
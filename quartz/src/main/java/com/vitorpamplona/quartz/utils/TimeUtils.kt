package com.vitorpamplona.quartz.utils

import com.vitorpamplona.quartz.crypto.CryptoUtils

object TimeUtils {
    const val oneMinute = 60
    const val fiveMinutes = 5 * oneMinute
    const val oneHour = 60 * oneMinute
    const val eightHours = 8 * oneHour
    const val oneDay = 24 * oneHour
    const val oneWeek = 7 * oneDay

    fun now() = System.currentTimeMillis() / 1000
    fun fiveMinutesAgo() = now() - fiveMinutes
    fun oneHourAgo() = now() - oneHour
    fun oneHourAhead() = now() + oneHour
    fun oneDayAgo() = now() - oneDay
    fun eightHoursAgo() = now() - eightHours
    fun oneWeekAgo() = now() - oneWeek
    fun randomWithinAWeek() = System.currentTimeMillis() / 1000 - CryptoUtils.randomInt(oneWeek)
}

package com.vitorpamplona.amethyst.model

object TimeUtils {
    const val fiveMinutes = 60 * 5
    const val oneHour = 60 * 60
    const val oneDay = 24 * 60 * 60

    fun now() = System.currentTimeMillis() / 1000
    fun fiveMinutesAgo() = now() - fiveMinutes
    fun oneHourAgo() = now() - oneHour
    fun oneDayAgo() = now() - oneDay
    fun eightHoursAgo() = now() - (oneHour * 8)
}

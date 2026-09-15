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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.fitness

import androidx.compose.runtime.Composable
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.my_fitness_unit_ft
import com.vitorpamplona.amethyst.commons.resources.my_fitness_unit_km
import com.vitorpamplona.amethyst.commons.resources.my_fitness_unit_m
import com.vitorpamplona.amethyst.commons.resources.my_fitness_unit_mi
import com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.phonePrefersMiles
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.DistanceTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.Elevation
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Display helpers for the My Fitness dashboard. The unit choice follows the phone's own
 * measurement preference, the same way the workout composer does, so a user never sees
 * kilometres on a screen where the rest of the app shows them miles.
 */
internal fun prefersMiles(): Boolean = phonePrefersMiles()

/** `7h 12m` / `42m` / `45s` — a total, so hours run past 24 rather than wrapping. */
internal fun formatDuration(totalSeconds: Long): String {
    if (totalSeconds <= 0) return "0m"

    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60

    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${totalSeconds}s"
    }
}

/** Distance in the user's unit, to one decimal — the bare number, paired with [distanceUnit]. */
internal fun formatDistanceValue(
    meters: Double,
    miles: Boolean,
): String {
    val value = if (miles) meters / DistanceTag.METERS_PER_MILE else meters / 1000.0
    return ((value * 10).roundToLong() / 10.0).toString()
}

@Composable
internal fun distanceUnit(miles: Boolean): String = if (miles) stringRes(Res.string.my_fitness_unit_mi) else stringRes(Res.string.my_fitness_unit_km)

/** Climb reads better as a whole number of metres or feet than as a decimal. */
internal fun formatElevationValue(
    meters: Double,
    miles: Boolean,
): String {
    val value = if (miles) meters / Elevation.METERS_PER_FOOT else meters
    return value.roundToInt().toString()
}

@Composable
internal fun elevationUnit(miles: Boolean): String = if (miles) stringRes(Res.string.my_fitness_unit_ft) else stringRes(Res.string.my_fitness_unit_m)

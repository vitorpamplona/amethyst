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
package com.vitorpamplona.amethyst.commons.frontpage

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.nip34Git.checkout.GitCheckoutCommands
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTask
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Where the reader's weather is for: kept in the account's schedule as the Observer's `place` param
 * (`["param", "place", name, latitude, longitude, "us" | "metric"]`, private like every other tag
 * Amethyst writes), so every device prints the same city. The coordinates are rounded to 0.01°
 * (about a kilometre): a forecast needs no more, and the schedule holds no more.
 */
@Immutable
@Serializable
data class ObserverPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    /** °F (the US' units) rather than °C. */
    val fahrenheit: Boolean = false,
)

/** One day of a forecast, on the place's own calendar. */
@Immutable
@Serializable
data class WeatherDay(
    /** `yyyy-MM-dd`. */
    val date: String,
    val high: Int? = null,
    val low: Int? = null,
    /** The day's highest chance of rain, in percent. */
    val rain: Int? = null,
    /** WMO weather code ([ObserverWeather.words]). */
    val code: Int? = null,
)

/**
 * The forecast for the schedule's place, fetched when a print starts: the paper's Weather desk
 * ([ObserverWeather.desk]), which the writer sets like any other.
 */
@Immutable
@Serializable
data class WeatherReport(
    val place: String,
    val fahrenheit: Boolean,
    /** The place's wall-clock time of the reading, `HH:mm`. */
    val asOf: String? = null,
    val now: Int? = null,
    val nowCode: Int? = null,
    /** Today first, then the next days. */
    val days: List<WeatherDay> = emptyList(),
    val sunrise: String? = null,
    val sunset: String? = null,
)

/** The Observer's weather: the `place` param, the Open-Meteo requests and readings, and the Weather desk the writer reads. */
object ObserverWeather {
    const val PARAM = FrontPageParams.PLACE
    private const val US = "us"
    private const val METRIC = "metric"

    /** Days the forecast covers, today included. */
    const val DAYS = 4

    /** How long a print waits for the forecast before it keeps its paper without one. */
    const val FETCH_TIMEOUT_MS = 10_000L

    const val CREDIT = "Weather data by Open-Meteo.com"

    private val json = Json { ignoreUnknownKeys = true }

    /** The schedule's place; null when it names none, or one that can't be read. */
    fun place(task: ScheduledTask?): ObserverPlace? {
        val values = task?.param(PARAM)?.values ?: return null
        val name = values.getOrNull(0)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val latitude = values.getOrNull(1)?.toDoubleOrNull()?.takeIf { it in -90.0..90.0 } ?: return null
        val longitude = values.getOrNull(2)?.toDoubleOrNull()?.takeIf { it in -180.0..180.0 } ?: return null
        return ObserverPlace(name, latitude, longitude, fahrenheit = values.getOrNull(3)?.trim()?.lowercase() == US)
    }

    /** [task] with [place] as its `place` param, rounded; null removes it (no weather). */
    fun withPlace(
        task: ScheduledTask,
        place: ObserverPlace?,
    ): ScheduledTask =
        if (place == null) {
            task.withoutParam(PARAM)
        } else {
            task.withParam(PARAM, place.name.trim(), coordinate(place.latitude), coordinate(place.longitude), if (place.fahrenheit) US else METRIC)
        }

    /** A coordinate to 0.01°, written without a trailing zero run or exponent. */
    fun coordinate(value: Double): String {
        val hundredths = (value * 100).roundToLong()
        val sign = if (hundredths < 0) "-" else ""
        val abs = abs(hundredths)
        val whole = abs / 100
        val frac = abs % 100
        return when {
            frac == 0L -> "$sign$whole"
            frac % 10 == 0L -> "$sign$whole.${frac / 10}"
            else -> "$sign$whole.${frac.toString().padStart(2, '0')}"
        }
    }

    /** Open-Meteo's forecast for [place]: now, and [DAYS] days with highs, lows, rain and sun, on the place's own clock. */
    fun forecastUrl(place: ObserverPlace): String =
        "https://api.open-meteo.com/v1/forecast?latitude=${coordinate(place.latitude)}&longitude=${coordinate(place.longitude)}" +
            "&current=temperature_2m,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset" +
            "&timezone=auto&forecast_days=$DAYS" +
            (if (place.fahrenheit) "&temperature_unit=fahrenheit" else "")

    /** Open-Meteo's place search for what the reader typed. */
    fun searchUrl(query: String): String = "https://geocoding-api.open-meteo.com/v1/search?name=${GitCheckoutCommands.percentEncode(query.trim())}&count=8&language=en&format=json"

    /** The places a search answered, each named with its region and country; °F where the country reads it. */
    fun parsePlaces(body: String): List<ObserverPlace> {
        val results = (parse(body) as? JsonObject)?.get("results") as? JsonArray ?: return emptyList()
        return results.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val name = o.string("name") ?: return@mapNotNull null
            val latitude = o.double("latitude") ?: return@mapNotNull null
            val longitude = o.double("longitude") ?: return@mapNotNull null
            val label = listOfNotNull(name, o.string("admin1")?.takeIf { it != name }, o.string("country")).joinToString(", ")
            ObserverPlace(label, latitude, longitude, fahrenheit = o.string("country_code")?.uppercase() in FAHRENHEIT_COUNTRIES)
        }
    }

    /** A forecast's body as the paper keeps it; null when it isn't one. */
    fun parseForecast(
        body: String,
        place: ObserverPlace,
    ): WeatherReport? {
        val root = parse(body) as? JsonObject ?: return null
        val current = root["current"] as? JsonObject
        val daily = root["daily"] as? JsonObject ?: return null
        val dates = daily.strings("time")
        if (dates.isEmpty()) return null
        val highs = daily.numbers("temperature_2m_max")
        val lows = daily.numbers("temperature_2m_min")
        val rain = daily.numbers("precipitation_probability_max")
        val codes = daily.numbers("weather_code")
        val days =
            dates.mapIndexed { i, date ->
                WeatherDay(
                    date = date,
                    high = highs.getOrNull(i)?.roundToInt(),
                    low = lows.getOrNull(i)?.roundToInt(),
                    rain = rain.getOrNull(i)?.roundToInt(),
                    code = codes.getOrNull(i)?.roundToInt(),
                )
            }
        return WeatherReport(
            place = place.name,
            fahrenheit = place.fahrenheit,
            asOf = current?.string("time")?.substringAfter('T', "")?.takeIf { it.length == 5 },
            now = current?.double("temperature_2m")?.roundToInt(),
            nowCode = current?.double("weather_code")?.roundToInt(),
            days = days,
            sunrise =
                daily
                    .strings("sunrise")
                    .firstOrNull()
                    ?.substringAfter('T', "")
                    ?.takeIf { it.isNotEmpty() },
            sunset =
                daily
                    .strings("sunset")
                    .firstOrNull()
                    ?.substringAfter('T', "")
                    ?.takeIf { it.isNotEmpty() },
        )
    }

    /** WMO weather interpretation codes, as a newspaper says them (the writer puts them in the paper's language). */
    fun words(code: Int?): String? =
        when (code) {
            null -> null
            0 -> "clear"
            1 -> "mainly clear"
            2 -> "partly cloudy"
            3 -> "overcast"
            45, 48 -> "fog"
            51, 53, 55 -> "drizzle"
            56, 57 -> "freezing drizzle"
            61, 63 -> "rain"
            65 -> "heavy rain"
            66, 67 -> "freezing rain"
            71, 73, 77 -> "snow"
            75 -> "heavy snow"
            80, 81 -> "showers"
            82 -> "violent showers"
            85, 86 -> "snow showers"
            95 -> "thunderstorms"
            96, 99 -> "thunderstorms with hail"
            else -> null
        }

    private val WEEKDAYS = arrayOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    /**
     * The paper's Weather desk, as the digest gives it to the writer: the forecast, the figures to
     * set as given, and the credit Open-Meteo asks for (CC BY 4.0). A desk like the others: the
     * writer decides where it runs, a box of its own, never the lead. [namesPlace] is false while the
     * paper goes to the reader's public Observer site: then the place isn't given, so the page can't
     * say where the reader lives.
     */
    fun desk(
        report: WeatherReport,
        namesPlace: Boolean,
    ): List<String> {
        val unit = if (report.fahrenheit) "°F" else "°C"
        val out = mutableListOf<String>()
        out +=
            if (namesPlace) {
                "For ${report.place}."
            } else {
                "The place is not given: this paper is published on the reader's public site. Head the box \"Weather\" and name no place."
            }
        if (report.now != null) out += "Now${report.asOf?.let { " ($it)" }.orEmpty()}: ${report.now}$unit${words(report.nowCode)?.let { ", $it" }.orEmpty()}."
        report.days.forEachIndexed { i, day ->
            val name = if (i == 0) "Today" else dayOfWeek(day.date)?.let { WEEKDAYS[it - 1] } ?: day.date
            val parts =
                listOfNotNull(
                    if (day.high != null && day.low != null) "high ${day.high}$unit, low ${day.low}$unit" else null,
                    words(day.code),
                    day.rain?.let { "$it% chance of rain" },
                    if (i == 0 && report.sunrise != null && report.sunset != null) "sunrise ${report.sunrise}, sunset ${report.sunset}" else null,
                )
            if (parts.isNotEmpty()) out += "$name (${day.date}): ${parts.joinToString(", ")}."
        }
        out += "Credit it where it runs, as written: \"$CREDIT\". It is not news and not from Nostr: set it as a box of its own (a rail, an ear, a corner), never the lead, the figures as given, nothing added."
        return out
    }

    /** Countries that read temperatures in °F. */
    private val FAHRENHEIT_COUNTRIES = setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW", "FM", "MH")

    private fun parse(body: String): JsonElement? =
        try {
            json.parseToJsonElement(body)
        } catch (e: IllegalArgumentException) {
            null
        }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

    private fun JsonObject.strings(key: String): List<String> = (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.orEmpty()

    private fun JsonObject.numbers(key: String): List<Double?> = (this[key] as? JsonArray)?.map { (it as? JsonPrimitive)?.doubleOrNull }.orEmpty()
}

/**
 * The forecast's body for [url] (an Open-Meteo request, [ObserverWeather]), through the client the
 * reader's network settings pick for fetching public pages; null when it can't be had.
 */
expect suspend fun IRoleBasedHttpClientBuilder.fetchWeatherText(url: String): String?

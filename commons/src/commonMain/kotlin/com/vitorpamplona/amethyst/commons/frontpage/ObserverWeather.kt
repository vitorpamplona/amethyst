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
 * The forecast fetched for a paper when it was printed, kept beside it ([SavedEdition.weather]) and
 * never inside its page: the app draws it over the paper it shows ([ObserverWeather.withStrip]), so
 * no copy of the page that leaves the device (the public site, a download) names the reader's city.
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

/** The kinds of weather the strip names, from the WMO codes Open-Meteo answers with ([ObserverWeather.condition]). */
enum class WeatherCondition {
    CLEAR,
    MAINLY_CLEAR,
    PARTLY_CLOUDY,
    OVERCAST,
    FOG,
    DRIZZLE,
    FREEZING_DRIZZLE,
    RAIN,
    HEAVY_RAIN,
    FREEZING_RAIN,
    SNOW,
    HEAVY_SNOW,
    SHOWERS,
    VIOLENT_SHOWERS,
    SNOW_SHOWERS,
    THUNDERSTORM,
    THUNDERSTORM_HAIL,
}

/**
 * The strip's words, in the reader's language: commons has no resources, so the screen resolves
 * them from commonsUI's and hands them in. [ENGLISH] is for tests and a platform without them.
 */
@Immutable
data class WeatherLabels(
    val title: String,
    val today: String,
    val high: String,
    val low: String,
    val rain: String,
    val sun: String,
    /** Monday first, seven of them. */
    val weekdays: List<String>,
    val conditions: Map<WeatherCondition, String>,
) {
    companion object {
        val ENGLISH =
            WeatherLabels(
                title = "Weather",
                today = "Today",
                high = "high",
                low = "low",
                rain = "rain",
                sun = "sun",
                weekdays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"),
                conditions =
                    mapOf(
                        WeatherCondition.CLEAR to "Clear",
                        WeatherCondition.MAINLY_CLEAR to "Mainly clear",
                        WeatherCondition.PARTLY_CLOUDY to "Partly cloudy",
                        WeatherCondition.OVERCAST to "Overcast",
                        WeatherCondition.FOG to "Fog",
                        WeatherCondition.DRIZZLE to "Drizzle",
                        WeatherCondition.FREEZING_DRIZZLE to "Freezing drizzle",
                        WeatherCondition.RAIN to "Rain",
                        WeatherCondition.HEAVY_RAIN to "Heavy rain",
                        WeatherCondition.FREEZING_RAIN to "Freezing rain",
                        WeatherCondition.SNOW to "Snow",
                        WeatherCondition.HEAVY_SNOW to "Heavy snow",
                        WeatherCondition.SHOWERS to "Showers",
                        WeatherCondition.VIOLENT_SHOWERS to "Violent showers",
                        WeatherCondition.SNOW_SHOWERS to "Snow showers",
                        WeatherCondition.THUNDERSTORM to "Thunderstorms",
                        WeatherCondition.THUNDERSTORM_HAIL to "Thunderstorms with hail",
                    ),
            )
    }
}

/** The Observer's weather: the `place` param, the Open-Meteo requests and readings, and the strip the app draws. */
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

    /** A kept report as text, for the paper's record; null for none. */
    fun encode(report: WeatherReport?): String? = report?.let { json.encodeToString(WeatherReport.serializer(), it) }

    /** A kept report from its text; null when it isn't one. */
    fun decode(text: String?): WeatherReport? =
        text?.takeIf { it.isNotBlank() }?.let {
            try {
                json.decodeFromString(WeatherReport.serializer(), it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }

    /** WMO weather interpretation codes, as the kinds of weather a newspaper names. */
    fun condition(code: Int?): WeatherCondition? =
        when (code) {
            null -> null
            0 -> WeatherCondition.CLEAR
            1 -> WeatherCondition.MAINLY_CLEAR
            2 -> WeatherCondition.PARTLY_CLOUDY
            3 -> WeatherCondition.OVERCAST
            45, 48 -> WeatherCondition.FOG
            51, 53, 55 -> WeatherCondition.DRIZZLE
            56, 57 -> WeatherCondition.FREEZING_DRIZZLE
            61, 63 -> WeatherCondition.RAIN
            65 -> WeatherCondition.HEAVY_RAIN
            66, 67 -> WeatherCondition.FREEZING_RAIN
            71, 73, 77 -> WeatherCondition.SNOW
            75 -> WeatherCondition.HEAVY_SNOW
            80, 81 -> WeatherCondition.SHOWERS
            82 -> WeatherCondition.VIOLENT_SHOWERS
            85, 86 -> WeatherCondition.SNOW_SHOWERS
            95 -> WeatherCondition.THUNDERSTORM
            96, 99 -> WeatherCondition.THUNDERSTORM_HAIL
            else -> null
        }

    /** [code] in [labels]' words; null when it names no weather [labels] has a word for. */
    fun words(
        code: Int?,
        labels: WeatherLabels = WeatherLabels.ENGLISH,
    ): String? = condition(code)?.let { labels.conditions[it] }

    /**
     * The weather as a strip under the paper's dateline, in [labels]' words (the reader's language,
     * from the app's resources): now, today's high and low, rain and sun, the next days, and
     * Open-Meteo's credit (CC BY 4.0, kept as written). Every value is escaped; it carries its own
     * styles, on the house tokens, and nothing that fetches or runs.
     */
    fun strip(
        report: WeatherReport,
        labels: WeatherLabels = WeatherLabels.ENGLISH,
    ): String {
        val unit = if (report.fahrenheit) "°F" else "°C"
        val today = report.days.firstOrNull()
        val later = report.days.drop(1)
        val out = StringBuilder()
        out.append("<div class=\"weather-strip\" data-credit=\"weather\">")
        out.append("<style>")
        out.append(".weather-strip{display:flex;flex-wrap:wrap;align-items:baseline;gap:4px 18px;padding:8px 0;border-bottom:1px solid var(--rule);font-family:var(--util);font-size:.78rem;color:var(--ink-2)}")
        out.append(".weather-strip .ws-place{font-weight:700;letter-spacing:.14em;text-transform:uppercase;font-size:.66rem;color:var(--ink-3)}")
        out.append(".weather-strip .ws-now b{font-family:var(--serif);font-size:1.25rem;color:var(--ink)}")
        out.append(".weather-strip .ws-credit{margin-left:auto;font-size:.66rem;color:var(--ink-3)}")
        out.append("@media (max-width:720px){.weather-strip .ws-credit{margin-left:0;flex-basis:100%}}")
        out.append("</style>")
        out.append("<span class=\"ws-place\">").append(escape("${labels.title} · ${report.place}")).append("</span>")
        if (report.now != null) {
            out
                .append("<span class=\"ws-now\"><b>")
                .append(report.now)
                .append(unit)
                .append("</b>")
            words(report.nowCode, labels)?.let { out.append(' ').append(escape(it)) }
            out.append("</span>")
        }
        if (today != null) {
            val parts = mutableListOf<String>()
            if (today.high != null && today.low != null) parts += "${labels.high} ${today.high}°, ${labels.low} ${today.low}°"
            words(today.code, labels)?.let { parts += it }
            today.rain?.let { parts += "$it% ${labels.rain}" }
            if (report.sunrise != null && report.sunset != null) parts += "${labels.sun} ${report.sunrise}–${report.sunset}"
            if (parts.isNotEmpty()) out.append("<span class=\"ws-today\">").append(escape("${labels.today}: " + parts.joinToString(", "))).append("</span>")
        }
        later.forEach { day ->
            val name = dayOfWeek(day.date)?.let { labels.weekdays.getOrNull(it - 1) } ?: return@forEach
            val temps = if (day.high != null && day.low != null) " ${day.high}°/${day.low}°" else ""
            val rain = day.rain?.let { " $it%" }.orEmpty()
            out.append("<span class=\"ws-day\">").append(escape("$name$temps$rain")).append("</span>")
        }
        out.append("<span class=\"ws-credit\">").append(escape(CREDIT + (report.asOf?.let { " · $it" } ?: ""))).append("</span>")
        out.append("</div>")
        return out.toString()
    }

    /**
     * [html] with the [report] strip under its dateline (else under its masthead, else at the top of
     * its sheet or body), for the screen only: what is kept, shared and published stays without it.
     */
    fun withStrip(
        html: String,
        report: WeatherReport,
        labels: WeatherLabels = WeatherLabels.ENGLISH,
    ): String {
        val strip = strip(report, labels)
        val at = afterElement(html, "dateline") ?: afterElement(html, "masthead") ?: afterOpening(html, "class=\"sheet\"") ?: afterOpening(html, "<body")
        return if (at == null) strip + html else html.substring(0, at) + strip + html.substring(at)
    }

    /**
     * Right after the element whose class list starts with [cls], found by its own closing tag:
     * nested elements of the same name (a dateline with a `<div>` inside) are counted, not taken
     * for its end. Null when there is none, or it never closes.
     */
    private fun afterElement(
        html: String,
        cls: String,
    ): Int? {
        val attr = Regex("class=\"$cls[\" ]").find(html)?.range?.first ?: return null
        val open = html.lastIndexOf('<', attr).takeIf { it >= 0 } ?: return null
        val name =
            Regex("^<([a-zA-Z][a-zA-Z0-9]*)")
                .find(html.substring(open, minOf(html.length, open + 20)))
                ?.groupValues
                ?.get(1)
                ?.lowercase() ?: return null
        val tags = Regex("<(/?)$name\\b[^>]*>", RegexOption.IGNORE_CASE)
        var depth = 0
        tags.findAll(html, open).forEach { tag ->
            depth += if (tag.groupValues[1].isEmpty()) 1 else -1
            if (depth == 0) return tag.range.last + 1
        }
        return null
    }

    /** Right after the opening tag that contains [marker]. */
    private fun afterOpening(
        html: String,
        marker: String,
    ): Int? {
        val start = html.indexOf(marker)
        if (start < 0) return null
        val end = html.indexOf('>', start)
        return if (end < 0) null else end + 1
    }

    private fun escape(text: String): String =
        buildString(text.length) {
            text.forEach {
                when (it) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(it)
                }
            }
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

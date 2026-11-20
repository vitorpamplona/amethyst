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

import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverSchedule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Observer's weather: the `place` param, Open-Meteo's answers, and the strip only the screen shows. */
class ObserverWeatherTest {
    private val schedule = ObserverSchedule.new("UTC", 1_795_000_000L).withEnabled(true, 1_795_000_000L)
    private val somerville = ObserverPlace("Somerville, Massachusetts, United States", 42.387597, -71.099497, fahrenheit = true)

    private val forecast =
        """
        {"latitude":42.38,"longitude":-71.1,"timezone":"America/New_York",
         "current":{"time":"2026-11-20T07:45","interval":900,"temperature_2m":41.8,"weather_code":0},
         "daily":{"time":["2026-11-20","2026-11-21","2026-11-22","2026-11-23"],
           "weather_code":[3,3,82,80],
           "temperature_2m_max":[64.6,61.2,60.9,63.0],
           "temperature_2m_min":[36.4,35.9,46.1,45.8],
           "precipitation_probability_max":[0,0,80,93],
           "sunrise":["2026-11-20T06:41","2026-11-21T06:42","2026-11-22T06:43","2026-11-23T06:44"],
           "sunset":["2026-11-20T16:18","2026-11-21T16:17","2026-11-22T16:17","2026-11-23T16:16"]}}
        """.trimIndent()

    @Test
    fun thePlaceRoundTripsRoundedThroughItsParam() {
        val task = ObserverWeather.withPlace(schedule, somerville)
        assertEquals(listOf("Somerville, Massachusetts, United States", "42.39", "-71.1", "us"), task.paramValues(FrontPageParams.PLACE))
        val back = assertNotNull(ObserverWeather.place(task))
        assertEquals(42.39, back.latitude)
        assertEquals(-71.1, back.longitude)
        assertTrue(back.fahrenheit)
        // Understood here, and a printer still runs it.
        assertTrue(FrontPageParams.understands(task))
        assertTrue(devicesPrint(task))
        // Removed: no weather.
        assertNull(ObserverWeather.place(ObserverWeather.withPlace(task, null)))
    }

    @Test
    fun aPlaceThatCantBeReadIsNoPlace() {
        assertNull(ObserverWeather.place(schedule))
        assertNull(ObserverWeather.place(schedule.withParam(FrontPageParams.PLACE, "Nowhere")))
        assertNull(ObserverWeather.place(schedule.withParam(FrontPageParams.PLACE, "Pole", "91", "0")))
        assertNull(ObserverWeather.place(schedule.withParam(FrontPageParams.PLACE, " ", "1", "2")))
        assertFalse(assertNotNull(ObserverWeather.place(schedule.withParam(FrontPageParams.PLACE, "Lisbon", "38.72", "-9.14"))).fahrenheit)
    }

    @Test
    fun coordinatesAreWrittenPlainly() {
        assertEquals("0", ObserverWeather.coordinate(0.001))
        assertEquals("-0.05", ObserverWeather.coordinate(-0.05))
        assertEquals("12.5", ObserverWeather.coordinate(12.4999))
        assertEquals("-71.1", ObserverWeather.coordinate(-71.099497))
        assertEquals("179.99", ObserverWeather.coordinate(179.994))
    }

    @Test
    fun theForecastAsksForThePlacesUnits() {
        val us = ObserverWeather.forecastUrl(ObserverWeather.place(ObserverWeather.withPlace(schedule, somerville))!!)
        assertTrue(us.startsWith("https://api.open-meteo.com/v1/forecast?latitude=42.39&longitude=-71.1&"))
        assertTrue(us.contains("&temperature_unit=fahrenheit"))
        assertFalse(ObserverWeather.forecastUrl(somerville.copy(fahrenheit = false)).contains("temperature_unit"))
        assertTrue(ObserverWeather.searchUrl("São Paulo ").endsWith("name=S%C3%A3o%20Paulo&count=8&language=en&format=json"))
    }

    @Test
    fun aForecastIsReadAsThePaperKeepsIt() {
        val report = assertNotNull(ObserverWeather.parseForecast(forecast, somerville))
        assertEquals("07:45", report.asOf)
        assertEquals(42, report.now)
        assertEquals("Clear", ObserverWeather.words(report.nowCode))
        assertEquals(4, report.days.size)
        assertEquals(WeatherDay("2026-11-20", high = 65, low = 36, rain = 0, code = 3), report.days[0])
        assertEquals("06:41", report.sunrise)
        assertEquals("16:18", report.sunset)
        // Kept beside the paper and in its record as JSON.
        assertEquals(report, ObserverWeather.decode(ObserverWeather.encode(report)))
        assertNull(ObserverWeather.parseForecast("not json", somerville))
        assertNull(ObserverWeather.parseForecast("{\"current\":{}}", somerville))
        assertNull(ObserverWeather.decode("{broken"))
    }

    @Test
    fun aSearchNamesEachPlaceWithItsRegion() {
        val body =
            """
            {"results":[
              {"name":"Somerville","latitude":42.38,"longitude":-71.1,"country_code":"US","country":"United States","admin1":"Massachusetts"},
              {"name":"Lisboa","latitude":38.72,"longitude":-9.13,"country_code":"PT","country":"Portugal","admin1":"Lisboa"},
              {"name":"No coordinates"}
            ]}
            """.trimIndent()
        val places = ObserverWeather.parsePlaces(body)
        assertEquals(listOf("Somerville, Massachusetts, United States", "Lisboa, Portugal"), places.map { it.name })
        assertEquals(listOf(true, false), places.map { it.fahrenheit })
        assertEquals(emptyList(), ObserverWeather.parsePlaces("{}"))
    }

    @Test
    fun theStripGoesUnderTheDatelineEscaped() {
        val report = ObserverWeather.parseForecast(forecast, somerville.copy(name = "<b>Somerville</b>"))!!
        val page =
            "<html><head></head><body><div class=\"sheet\"><div class=\"folio\"><span>x</span></div>" +
                "<header class=\"masthead\"><h1>The Nostr Observer</h1></header><div class=\"dateline\"><span>a</span><span>b</span></div><section class=\"fold\"></section></div></body></html>"
        val shown = ObserverWeather.withStrip(page, report)
        val strip = shown.indexOf("class=\"weather-strip\"")
        assertTrue(strip > shown.indexOf("class=\"dateline\""))
        assertTrue(strip < shown.indexOf("class=\"fold\""))
        assertTrue(shown.contains("&lt;b&gt;Somerville&lt;/b&gt;"))
        assertFalse(shown.contains("<b>Somerville</b>"))
        assertTrue(shown.contains("42°F"))
        assertTrue(shown.contains("Saturday 61°/36° 0%"))
        assertTrue(shown.contains(ObserverWeather.CREDIT))
        // Nothing in it fetches.
        assertFalse(Regex("(?i)url\\(|src=|href=|@import").containsMatchIn(ObserverWeather.strip(report)))
        // Without a dateline, under the masthead; without either, at the top of the sheet.
        val noDateline = ObserverWeather.withStrip(page.replace("<div class=\"dateline\"><span>a</span><span>b</span></div>", ""), report)
        assertTrue(noDateline.indexOf("weather-strip") > noDateline.indexOf("</header>"))
        val bare = ObserverWeather.withStrip("<body><p>x</p></body>", report)
        assertTrue(bare.indexOf("weather-strip") in bare.indexOf("<body>")..bare.indexOf("<p>"))
    }

    @Test
    fun theStripSpeaksTheReadersLanguage() {
        val report = ObserverWeather.parseForecast(forecast, somerville.copy(fahrenheit = false))!!
        val pt =
            WeatherLabels(
                title = "Tempo",
                today = "Hoje",
                high = "máx.",
                low = "mín.",
                rain = "chuva",
                sun = "sol",
                weekdays = listOf("segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo"),
                conditions = mapOf(WeatherCondition.CLEAR to "Céu limpo", WeatherCondition.OVERCAST to "Nublado"),
            )
        val strip = ObserverWeather.strip(report, pt)
        assertTrue(strip.contains("Tempo · Somerville"))
        assertTrue(strip.contains("Céu limpo"))
        assertTrue(strip.contains("Hoje: máx. 65°, mín. 36°, Nublado, 0% chuva, sol 06:41–16:18"))
        assertTrue(strip.contains("sábado 61°/36° 0%"))
        // The credit stays as Open-Meteo asks for it.
        assertTrue(strip.contains(ObserverWeather.CREDIT))
        // A code with no word in the labels is left out rather than shown in English.
        assertNull(ObserverWeather.words(95, pt))
    }

    @Test
    fun aDatelineWithADivInsideIsSteppedOverWhole() {
        val report = ObserverWeather.parseForecast(forecast, somerville)!!
        val page = "<body><div class=\"dateline\"><div><span>a</span></div><span>b</span></div><section class=\"fold\"></section></body>"
        val shown = ObserverWeather.withStrip(page, report)
        // After the dateline's own close, not its inner div's.
        assertTrue(shown.indexOf("weather-strip") > shown.indexOf("<span>b</span></div>"))
        assertTrue(shown.indexOf("weather-strip") < shown.indexOf("class=\"fold\""))
    }

    @Test
    fun theWriterReadsTheRulesAndTheLayoutsButNotTheNotes() {
        val forWriter = houseCssForWriter(HOUSE_CSS)
        assertTrue(forWriter.length < HOUSE_CSS.length * 3 / 4, "only ${HOUSE_CSS.length - forWriter.length} chars saved")
        assertTrue(forWriter.contains("color-scheme: light"))
        assertTrue(forWriter.contains(".agate {"))
        assertTrue(forWriter.contains("LAYOUTS THE MARKUP CAN USE"))
        assertTrue(forWriter.contains("figure.whole"))
        assertFalse(forWriter.contains("THE FOLIO GOES ON TOP"))
        assertFalse(forWriter.contains("\n\n"))
        assertTrue(systemPrompt().contains(forWriter))
    }

    @Test
    fun theRecordCarriesTheForecastToTheOtherDevices() {
        val report = ObserverWeather.parseForecast(forecast, somerville)!!
        val record = EditionRecord(day = "2026-11-20", printedAt = 1_795_160_000L, sha256 = "a".repeat(64), code = "C", weather = report)
        val back = assertNotNull(EditionRecord.parse(record.toTag()))
        assertEquals(report, back.weather)
        assertEquals(report, back.asSaved().weather)
        // A record without one still reads.
        assertNull(EditionRecord.parse(record.copy(weather = null).toTag())?.weather)
    }
}

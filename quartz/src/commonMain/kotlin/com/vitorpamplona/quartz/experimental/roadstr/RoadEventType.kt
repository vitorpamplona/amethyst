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
package com.vitorpamplona.quartz.experimental.roadstr

/**
 * Road event categories carried in the `t` tag of a Roadstr report (kind 1315).
 *
 * Each type also defines a client-side *effective* TTL: the relay-side NIP-40
 * `expiration` tag is always a fixed 14-day garbage-collection window, but
 * clients should stop showing a report after [effectiveTtlSeconds] from its
 * `created_at` (subject to confirmations).
 */
enum class RoadEventType(
    val code: String,
    val effectiveTtlSeconds: Long,
) {
    POLICE("police", 7_200L), // 2 hours
    SPEED_CAMERA("speed_camera", 2_592_000L), // 30 days
    TRAFFIC_JAM("traffic_jam", 3_600L), // 1 hour
    ACCIDENT("accident", 10_800L), // 3 hours
    ROAD_CLOSURE("road_closure", 604_800L), // 7 days
    CONSTRUCTION("construction", 604_800L), // 7 days
    HAZARD("hazard", 14_400L), // 4 hours
    ROAD_CONDITION("road_condition", 21_600L), // 6 hours
    POTHOLE("pothole", 604_800L), // 7 days
    FOG("fog", 10_800L), // 3 hours
    ICE("ice", 21_600L), // 6 hours
    ANIMAL("animal", 3_600L), // 1 hour
    OTHER("other", 7_200L), // 2 hours
    ;

    companion object {
        fun fromCode(code: String?): RoadEventType? = entries.firstOrNull { it.code == code }
    }
}

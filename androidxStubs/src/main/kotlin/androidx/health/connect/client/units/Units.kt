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
package androidx.health.connect.client.units

/**
 * JVM stand-ins for the Health Connect unit types.
 *
 * The conversions are real — a Length is metres and an Energy is kilocalories,
 * exactly as the platform defines them — so a workout mapped through these
 * would carry the right numbers. Nothing produces one here; see
 * [androidx.health.connect.client.HealthConnectClient].
 */
class Length private constructor(
    val inMeters: Double,
) {
    val inKilometers: Double get() = inMeters / 1000.0

    val inMiles: Double get() = inMeters / 1609.344

    val inFeet: Double get() = inMeters / 0.3048

    companion object {
        fun meters(value: Double) = Length(value)

        fun kilometers(value: Double) = Length(value * 1000.0)

        fun miles(value: Double) = Length(value * 1609.344)

        fun feet(value: Double) = Length(value * 0.3048)
    }
}

class Energy private constructor(
    val inKilocalories: Double,
) {
    val inCalories: Double get() = inKilocalories * 1000.0

    val inJoules: Double get() = inKilocalories * 4184.0

    val inKilojoules: Double get() = inKilocalories * 4.184

    companion object {
        fun kilocalories(value: Double) = Energy(value)

        fun calories(value: Double) = Energy(value / 1000.0)

        fun joules(value: Double) = Energy(value / 4184.0)

        fun kilojoules(value: Double) = Energy(value / 4.184)
    }
}

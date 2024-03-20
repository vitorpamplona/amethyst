/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.amethyst.commons.robohash.Black
import com.vitorpamplona.amethyst.commons.robohash.parts.accessory0Seven
import com.vitorpamplona.amethyst.commons.robohash.parts.accessory1Nose
import com.vitorpamplona.amethyst.commons.robohash.parts.accessory2HornRed
import com.vitorpamplona.amethyst.commons.robohash.parts.accessory3Button
import com.vitorpamplona.amethyst.commons.robohash.parts.accessory4Satellite
import com.vitorpamplona.amethyst.commons.robohash.parts.accessory5Mustache
import com.vitorpamplona.amethyst.commons.robohash.parts.accessory6Hat
import com.vitorpamplona.amethyst.commons.robohash.parts.accessory7Antenna
import com.vitorpamplona.amethyst.commons.robohash.parts.accessory8Brush
import com.vitorpamplona.amethyst.commons.robohash.parts.accessory9Horn
import com.vitorpamplona.amethyst.commons.robohash.parts.body0Trooper
import com.vitorpamplona.amethyst.commons.robohash.parts.body1Thin
import com.vitorpamplona.amethyst.commons.robohash.parts.body2Thinnest
import com.vitorpamplona.amethyst.commons.robohash.parts.body3Front
import com.vitorpamplona.amethyst.commons.robohash.parts.body4Round
import com.vitorpamplona.amethyst.commons.robohash.parts.body5Neck
import com.vitorpamplona.amethyst.commons.robohash.parts.body6IronMan
import com.vitorpamplona.amethyst.commons.robohash.parts.body7NeckThinner
import com.vitorpamplona.amethyst.commons.robohash.parts.body8Big
import com.vitorpamplona.amethyst.commons.robohash.parts.body9Huge
import com.vitorpamplona.amethyst.commons.robohash.parts.eyes0Squint
import com.vitorpamplona.amethyst.commons.robohash.parts.eyes1Round
import com.vitorpamplona.amethyst.commons.robohash.parts.eyes2Single
import com.vitorpamplona.amethyst.commons.robohash.parts.eyes3Scott
import com.vitorpamplona.amethyst.commons.robohash.parts.eyes4RoundSingle
import com.vitorpamplona.amethyst.commons.robohash.parts.eyes5RoundSmall
import com.vitorpamplona.amethyst.commons.robohash.parts.eyes6WallE
import com.vitorpamplona.amethyst.commons.robohash.parts.eyes7Bar
import com.vitorpamplona.amethyst.commons.robohash.parts.eyes8SmallBar
import com.vitorpamplona.amethyst.commons.robohash.parts.eyes9Shield
import com.vitorpamplona.amethyst.commons.robohash.parts.face0C3po
import com.vitorpamplona.amethyst.commons.robohash.parts.face1Rock
import com.vitorpamplona.amethyst.commons.robohash.parts.face2Long
import com.vitorpamplona.amethyst.commons.robohash.parts.face3Oval
import com.vitorpamplona.amethyst.commons.robohash.parts.face4Cylinder
import com.vitorpamplona.amethyst.commons.robohash.parts.face5Baloon
import com.vitorpamplona.amethyst.commons.robohash.parts.face6Triangle
import com.vitorpamplona.amethyst.commons.robohash.parts.face7Bent
import com.vitorpamplona.amethyst.commons.robohash.parts.face8TriangleInv
import com.vitorpamplona.amethyst.commons.robohash.parts.face9Square
import com.vitorpamplona.amethyst.commons.robohash.parts.mouth0Horz
import com.vitorpamplona.amethyst.commons.robohash.parts.mouth1Cylinder
import com.vitorpamplona.amethyst.commons.robohash.parts.mouth2Teeth
import com.vitorpamplona.amethyst.commons.robohash.parts.mouth3Grid
import com.vitorpamplona.amethyst.commons.robohash.parts.mouth4Vert
import com.vitorpamplona.amethyst.commons.robohash.parts.mouth5MidOpen
import com.vitorpamplona.amethyst.commons.robohash.parts.mouth6Cell
import com.vitorpamplona.amethyst.commons.robohash.parts.mouth7Happy
import com.vitorpamplona.amethyst.commons.robohash.parts.mouth8Buttons
import com.vitorpamplona.amethyst.commons.robohash.parts.mouth9Closed
import com.vitorpamplona.amethyst.commons.robohash.roboBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Benchmark, which will execute on an Android device.
 *
 * The body of [test] is measured in a loop, and Studio will output the
 * result. Modify your code to see how it affects performance.
 */
@RunWith(AndroidJUnit4::class)
class RobohashPartsBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()

    val fgColor: SolidColor = Black

    fun test(function: (builder: ImageVector.Builder) -> Unit) {
        benchmarkRule.measureRepeated {
            roboBuilder("Test") {
                function(this)
            }
        }
    }

    @Test
    fun body0Trooper(): Unit =
        test {
            body0Trooper(fgColor, it)
        }

    @Test
    fun body1Thin(): Unit =
        test {
            body1Thin(fgColor, it)
        }

    @Test
    fun body2Thinnest(): Unit =
        test {
            body2Thinnest(fgColor, it)
        }

    @Test
    fun body3Front(): Unit =
        test {
            body3Front(fgColor, it)
        }

    @Test
    fun body4Round(): Unit =
        test {
            body4Round(fgColor, it)
        }

    @Test
    fun body5Neck(): Unit =
        test {
            body5Neck(fgColor, it)
        }

    @Test
    fun body6IronMan(): Unit =
        test {
            body6IronMan(fgColor, it)
        }

    @Test
    fun body7NeckThinner(): Unit =
        test {
            body7NeckThinner(fgColor, it)
        }

    @Test
    fun body8Big(): Unit =
        test {
            body8Big(fgColor, it)
        }

    @Test
    fun body9Huge(): Unit =
        test {
            body9Huge(fgColor, it)
        }

    @Test
    fun face0C3po(): Unit =
        test {
            face0C3po(fgColor, it)
        }

    @Test
    fun face1Rock(): Unit =
        test {
            face1Rock(fgColor, it)
        }

    @Test
    fun face2Long(): Unit =
        test {
            face2Long(fgColor, it)
        }

    @Test
    fun face3Oval(): Unit =
        test {
            face3Oval(fgColor, it)
        }

    @Test
    fun face4Cylinder(): Unit =
        test {
            face4Cylinder(fgColor, it)
        }

    @Test
    fun face5Baloon(): Unit =
        test {
            face5Baloon(fgColor, it)
        }

    @Test
    fun face6Triangle(): Unit =
        test {
            face6Triangle(fgColor, it)
        }

    @Test
    fun face7Bent(): Unit =
        test {
            face7Bent(fgColor, it)
        }

    @Test
    fun face8TriangleInv(): Unit =
        test {
            face8TriangleInv(fgColor, it)
        }

    @Test
    fun face9Square(): Unit =
        test {
            face9Square(fgColor, it)
        }

    @Test
    fun eyes0Squint(): Unit =
        test {
            eyes0Squint(fgColor, it)
        }

    @Test
    fun eyes1Round(): Unit =
        test {
            eyes1Round(fgColor, it)
        }

    @Test
    fun eyes2Single(): Unit =
        test {
            eyes2Single(fgColor, it)
        }

    @Test
    fun eyes3Scott(): Unit =
        test {
            eyes3Scott(fgColor, it)
        }

    @Test
    fun eyes4RoundSingle(): Unit =
        test {
            eyes4RoundSingle(fgColor, it)
        }

    @Test
    fun eyes5RoundSmall(): Unit =
        test {
            eyes5RoundSmall(fgColor, it)
        }

    @Test
    fun eyes6WallE(): Unit =
        test {
            eyes6WallE(fgColor, it)
        }

    @Test
    fun eyes7Bar(): Unit =
        test {
            eyes7Bar(fgColor, it)
        }

    @Test
    fun eyes8SmallBar(): Unit =
        test {
            eyes8SmallBar(fgColor, it)
        }

    @Test
    fun eyes9Shield(): Unit =
        test {
            eyes9Shield(fgColor, it)
        }

    @Test
    fun mouth0Horz(): Unit =
        test {
            mouth0Horz(fgColor, it)
        }

    @Test
    fun mouth1Cylinder(): Unit =
        test {
            mouth1Cylinder(fgColor, it)
        }

    @Test
    fun mouth2Teeth(): Unit =
        test {
            mouth2Teeth(fgColor, it)
        }

    @Test
    fun mouth3Grid(): Unit =
        test {
            mouth3Grid(fgColor, it)
        }

    @Test
    fun mouth4Vert(): Unit =
        test {
            mouth4Vert(fgColor, it)
        }

    @Test
    fun mouth5MidOpen(): Unit =
        test {
            mouth5MidOpen(fgColor, it)
        }

    @Test
    fun mouth6Cell(): Unit =
        test {
            mouth6Cell(fgColor, it)
        }

    @Test
    fun mouth7Happy(): Unit =
        test {
            mouth7Happy(fgColor, it)
        }

    @Test
    fun mouth8Buttons(): Unit =
        test {
            mouth8Buttons(fgColor, it)
        }

    @Test
    fun mouth9Closed(): Unit =
        test {
            mouth9Closed(fgColor, it)
        }

    @Test
    fun accessory0Seven(): Unit =
        test {
            accessory0Seven(fgColor, it)
        }

    @Test
    fun accessory1Nose(): Unit =
        test {
            accessory1Nose(fgColor, it)
        }

    @Test
    fun accessory2HornRed(): Unit =
        test {
            accessory2HornRed(fgColor, it)
        }

    @Test
    fun accessory3Button(): Unit =
        test {
            accessory3Button(fgColor, it)
        }

    @Test
    fun accessory4Satellite(): Unit =
        test {
            accessory4Satellite(fgColor, it)
        }

    @Test
    fun accessory5Mustache(): Unit =
        test {
            accessory5Mustache(fgColor, it)
        }

    @Test
    fun accessory6Hat(): Unit =
        test {
            accessory6Hat(fgColor, it)
        }

    @Test
    fun accessory7Antenna(): Unit =
        test {
            accessory7Antenna(fgColor, it)
        }

    @Test
    fun accessory8Brush(): Unit =
        test {
            accessory8Brush(fgColor, it)
        }

    @Test
    fun accessory9Horn(): Unit =
        test {
            accessory9Horn(fgColor, it)
        }
}

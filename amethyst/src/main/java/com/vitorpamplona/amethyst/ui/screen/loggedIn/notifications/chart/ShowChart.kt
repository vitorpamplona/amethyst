/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.chart

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberEnd
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.shader.ShaderProvider
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.RoyalBlue

fun makeLine(color: Color): LineCartesianLayer.Line =
    LineCartesianLayer.Line(
        fill = LineCartesianLayer.LineFill.single(fill(color)),
        areaFill =
            LineCartesianLayer.AreaFill.single(
                fill(
                    ShaderProvider.verticalGradient(
                        color.copy(alpha = 0.4f).toArgb(),
                        Color.Transparent.toArgb(),
                    ),
                ),
            ),
        pointConnector = LineCartesianLayer.PointConnector.cubic(),
    )

val chartLayers =
    arrayOf(
        LineCartesianLayer(
            LineCartesianLayer.LineProvider.series(
                makeLine(RoyalBlue),
                makeLine(Color.Green),
                makeLine(Color.Red),
            ),
            verticalAxisPosition = Axis.Position.Vertical.Start,
        ),
        LineCartesianLayer(
            LineCartesianLayer.LineProvider.series(
                makeLine(BitcoinOrange),
            ),
            verticalAxisPosition = Axis.Position.Vertical.End,
        ),
    )

@Composable
fun ShowChart(model: CartesianChartModel) {
    val chart =
        rememberCartesianChart(
            layers = chartLayers,
            startAxis =
                VerticalAxis.rememberStart(
                    valueFormatter = CountAxisValueFormatter(),
                    itemPlacer = VerticalAxis.ItemPlacer.count({ 7 }),
                ),
            endAxis =
                VerticalAxis.rememberEnd(
                    label = rememberAxisLabelComponent(color = BitcoinOrange),
                    valueFormatter = AmountValueFormatter(),
                    itemPlacer = VerticalAxis.ItemPlacer.count({ 7 }),
                ),
            bottomAxis =
                HorizontalAxis.rememberBottom(
                    valueFormatter = LastWeekLabelFormatter(),
                ),
        )

    CartesianChartHost(
        chart = chart,
        model = model,
    )
}

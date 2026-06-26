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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.embed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.R as CommonsR

/**
 * A bottom **pull-up sheet** for JavaScript console output. Collapsed it's a small grabber at the
 * bottom edge of the embedded surface. Pull it up (or tap) to reveal a scrollable log of console
 * messages captured from the page via [WebChromeClient.onConsoleMessage]. The page can't draw over
 * it (the surface is z-ordered below this layer).
 *
 * Mirrors [TopControlSheet]'s pattern but anchored at the bottom using a [Box] with
 * [Alignment.BottomCenter].
 */
@Composable
fun BottomConsoleSheet(
    logs: List<ConsoleLogEntry>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        // Column anchored at BottomCenter. Grabber sits at the top of the column so it rides up
        // with the panel as it expands — standard bottom-sheet handle behaviour.
        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Grabber — the only touch target when collapsed; stays at the top of the panel when open
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                        .clickable { onExpandedChange(!expanded) }
                        .draggable(
                            orientation = Orientation.Vertical,
                            state =
                                rememberDraggableState { delta ->
                                    if (delta < -1f) {
                                        onExpandedChange(true)
                                    } else if (delta > 1f) {
                                        onExpandedChange(false)
                                    }
                                },
                        ).padding(horizontal = 16.dp, vertical = 7.dp),
            ) {
                Spacer(
                    Modifier
                        .width(36.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 3.dp,
                    shadowElevation = 6.dp,
                    shape = RoundedCornerShape(bottomStart = 0.dp, bottomEnd = 0.dp),
                ) {
                    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.4f).dp
                    Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(CommonsR.string.browser_console_title, logs.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onClear) {
                                Text(
                                    stringResource(CommonsR.string.browser_console_clear),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        HorizontalDivider()
                        val scrollState = rememberScrollState()
                        LaunchedEffect(logs.size) { scrollState.scrollTo(Int.MAX_VALUE) }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            logs.forEach { entry ->
                                ConsoleLogRow(entry)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsoleLogRow(entry: ConsoleLogEntry) {
    val levelColor = consoleLevelColor(entry.level)
    val dimColor = MaterialTheme.colorScheme.onSurfaceVariant
    val srcShort =
        entry.source
            .substringAfterLast("/")
            .substringAfterLast("\\")
            .let { if (it.isBlank()) entry.source.takeLast(20) else it }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            consoleLevelChar(entry.level),
            color = levelColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.width(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = levelColor)) {
                    append(entry.message)
                }
                if (srcShort.isNotBlank()) {
                    withStyle(SpanStyle(color = dimColor)) {
                        append("  $srcShort:${entry.lineNumber}")
                    }
                }
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
            overflow = TextOverflow.Visible,
        )
    }
}

@Composable
private fun consoleLevelColor(level: String): Color {
    val warningAmber = if (isSystemInDarkTheme()) Color(0xFFFFB74D) else Color(0xFFE65100)
    return when (level.uppercase()) {
        "ERROR" -> MaterialTheme.colorScheme.error
        "WARNING" -> warningAmber
        "TIP" -> MaterialTheme.colorScheme.tertiary
        "DEBUG" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
}

private fun consoleLevelChar(level: String): String =
    when (level.uppercase()) {
        "ERROR" -> "E"
        "WARNING" -> "W"
        "TIP" -> "T"
        "DEBUG" -> "D"
        else -> "I"
    }

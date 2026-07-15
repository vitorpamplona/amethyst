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
package com.vitorpamplona.amethyst.desktop.ui.scheduling

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.quartz.utils.TimeUtils
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val DISPLAY_FORMAT: DateTimeFormatter =
    DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())

private fun formatScheduledFor(epochSec: Long): String =
    Instant
        .ofEpochSecond(epochSec)
        .atZone(ZoneId.systemDefault())
        .format(DISPLAY_FORMAT)

/**
 * Millis that Material3's [DatePicker] should be seeded with so that the highlighted
 * day is the *local* calendar day of [epochSec].
 *
 * The DatePicker treats its `initialSelectedDateMillis` as a UTC instant and displays
 * the UTC calendar day. Passing `epochSec*1000` directly (a local instant reinterpreted
 * as UTC) shifts the shown day back by one in positive-offset zones (UTC+), so a
 * confirm-without-changing-the-date would schedule ~24h early. Instead we compute the
 * UTC-midnight of the local date, which the picker then renders as that same date.
 *
 * The confirm math in [DateTimePickerDialog] is the exact inverse: it reads
 * `selectedDateMillis` (UTC-midnight), adds the hour/minute, then subtracts the
 * system-default offset — so open→confirm-untouched round-trips to the same local day.
 */
internal fun datePickerInitialMillis(epochSec: Long): Long =
    Instant
        .ofEpochSecond(epochSec)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .atStartOfDay(ZoneOffset.UTC)
        .toEpochSecond() * 1000

/**
 * Desktop scheduling section. Shows quick presets and an inline summary row; tapping
 * the summary opens a two-stage date + time [Dialog]. The chosen instant is rounded
 * up to the next quarter-hour to match the periodic drain cadence. Adapted from the
 * Android `ScheduleAtPicker` (which used Material3 dialog scaffolds unavailable in the
 * same form on Desktop, so a plain [Dialog] is used here per Desktop dialog guidance).
 */
@Composable
fun DesktopScheduleAtPicker(
    scheduledForSec: Long,
    onChanged: (Long) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        ) {
            Icon(
                symbol = MaterialSymbols.Schedule,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Schedule",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            text = "Your note will be published from this app at the scheduled time (fires within about a minute while the app is open). The app must be running.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        PresetChips(onPick = onChanged)

        OutlinedCard(
            onClick = { showPicker = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(MaterialSymbols.Schedule, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                if (scheduledForSec < TimeUtils.now()) {
                    Text("Pick a date and time", style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text(
                        text = "Publishes ${formatScheduledFor(scheduledForSec)}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }

    if (showPicker) {
        DateTimePickerDialog(
            initialSec = if (scheduledForSec >= TimeUtils.now()) scheduledForSec else sanitizeScheduleTime(presetInOneHour()),
            onDismiss = { showPicker = false },
            onConfirm = {
                onChanged(it)
                showPicker = false
            },
        )
    }
}

@Composable
private fun PresetChips(onPick: (Long) -> Unit) {
    val scroll = rememberScrollState()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = { onPick(sanitizeScheduleTime(presetInOneHour())) },
            label = { Text("In 1 hour") },
        )
        AssistChip(
            onClick = { onPick(sanitizeScheduleTime(presetTomorrowMorning())) },
            label = { Text("Tomorrow 9 AM") },
        )
        AssistChip(
            onClick = { onPick(sanitizeScheduleTime(presetNextMondayMorning())) },
            label = { Text("Next Monday 9 AM") },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePickerDialog(
    initialSec: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    var stage by remember { mutableStateOf(Stage.DATE) }

    val currentTime =
        Instant
            .ofEpochSecond(initialSec)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()

    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = datePickerInitialMillis(initialSec),
            yearRange = currentTime.year..2050,
            selectableDates =
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= System.currentTimeMillis() - 86_400_000
                },
        )

    val timePickerState =
        rememberTimePickerState(
            initialHour = currentTime.hour,
            initialMinute = currentTime.minute,
            is24Hour = false,
        )

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                when (stage) {
                    Stage.DATE -> {
                        DatePicker(state = datePickerState)
                        Spacer(Modifier.size(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { stage = Stage.TIME }) { Text("Next") }
                        }
                    }

                    Stage.TIME -> {
                        TimePicker(state = timePickerState)
                        Spacer(Modifier.size(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            OutlinedButton(onClick = { stage = Stage.DATE }) { Text("Back") }
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    val dayMillisUtc = datePickerState.selectedDateMillis ?: (TimeUtils.oneDayAhead() * 1000)
                                    val datetimeLocalSec =
                                        (dayMillisUtc / 1000) +
                                            (timePickerState.hour * TimeUtils.ONE_HOUR) +
                                            (timePickerState.minute * TimeUtils.ONE_MINUTE)
                                    val offset: ZoneOffset = ZoneId.systemDefault().rules.getOffset(Instant.now())
                                    val rawSec = datetimeLocalSec - offset.totalSeconds
                                    onConfirm(sanitizeScheduleTime(rawSec))
                                },
                            ) { Text("Confirm") }
                        }
                    }
                }
            }
        }
    }
}

private enum class Stage {
    DATE,
    TIME,
}

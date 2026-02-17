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
package com.vitorpamplona.amethyst.ui.note.creators.polls

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.note.timeAheadNoDot
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.ShortNotePostViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.utils.TimeUtils
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PollDeadlinePicker(model: ShortNotePostViewModel) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Get current time details
    val currentTime = Instant.ofEpochMilli(model.closedAt * 1000).atZone(ZoneId.systemDefault()).toLocalDateTime()

    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = model.closedAt * 1000,
            yearRange = currentTime.year..2050,
            selectableDates =
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                        // Only allow today and future dates
                        return utcTimeMillis >= System.currentTimeMillis() - 86400000 // minus 24h buffer
                    }
                },
        )
    val timePickerState =
        rememberTimePickerState(
            initialHour = currentTime.hour,
            initialMinute = currentTime.minute,
            is24Hour = false, // Set to true if you prefer military time
        )

    val context = LocalContext.current

    OutlinedCard(
        onClick = { showDatePicker = true },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.DateRange, contentDescription = null)
            Spacer(Modifier.width(12.dp))

            if (model.closedAt < TimeUtils.oneMinuteFromNow()) {
                Text(stringRes(R.string.poll_closing_date_time) + " " + model.closedAt, style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(
                    text = stringRes(R.string.poll_closing_in, timeAheadNoDot(model.closedAt, context)),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }

    // --- Date Picker Dialog ---
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    showTimePicker = true
                }) { Text(stringResource(R.string.next)) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // --- Time Picker Dialog ---
    if (showTimePicker) {
        TimePickerDialog(
            title = {
                Text(stringResource(R.string.closing_time))
            },
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val datetimeLocalTimeZone =
                            datePickerState.selectedDateMillis?.let { localDayAtZeroHourMillis ->
                                (localDayAtZeroHourMillis / 1000) +
                                    (timePickerState.hour * TimeUtils.ONE_HOUR) +
                                    (timePickerState.minute * TimeUtils.ONE_MINUTE)
                            } ?: TimeUtils.oneDayAhead()

                        // Get the offset from UTC for the current instant in the local time zone
                        val offset: ZoneOffset = ZoneId.systemDefault().rules.getOffset(Instant.now())

                        model.closedAt = datetimeLocalTimeZone - offset.totalSeconds

                        showTimePicker = false
                    },
                ) { Text(stringResource(R.string.confirm)) }
            },
        ) {
            TimePicker(state = timePickerState)
        }
    }
}

@SuppressLint("ViewModelConstructorInComposable")
@Preview
@Composable
fun PollDeadlinePickerPreview() {
    PollDeadlinePicker(
        ShortNotePostViewModel(),
    )
}

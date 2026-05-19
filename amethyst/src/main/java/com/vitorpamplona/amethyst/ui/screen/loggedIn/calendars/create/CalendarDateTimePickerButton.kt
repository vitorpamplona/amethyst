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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.create

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale

/**
 * Tap-to-edit button that opens a Material3 DatePicker (and, when [includeTime] is true,
 * chains into a TimePicker). The resolved instant is converted to UTC epoch seconds using
 * the device's zone offset *at the picked moment*, so DST transitions are handled correctly.
 *
 * Pass `0L` for [unixSeconds] when the user hasn't picked anything yet — the button shows
 * [placeholder] instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarDateTimePickerButton(
    unixSeconds: Long,
    placeholder: String,
    includeTime: Boolean,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    val pretty =
        if (unixSeconds <= 0L) {
            placeholder
        } else if (includeTime) {
            DateFormat
                .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(unixSeconds * 1000))
        } else {
            SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date(unixSeconds * 1000))
        }

    val initialMillis = if (unixSeconds > 0L) unixSeconds * 1000L else System.currentTimeMillis()
    val initialLocal =
        Instant
            .ofEpochMilli(initialMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()

    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
        )
    val timePickerState =
        rememberTimePickerState(
            initialHour = initialLocal.hour,
            initialMinute = initialLocal.minute,
            is24Hour = false,
        )

    fun reset() {
        datePickerState.selectedDateMillis = initialMillis
        timePickerState.hour = initialLocal.hour
        timePickerState.minute = initialLocal.minute
    }

    OutlinedButton(
        onClick = { showDate = true },
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(pretty)
    }

    if (showDate) {
        DatePickerDialog(
            onDismissRequest = {
                reset()
                showDate = false
            },
            confirmButton = {
                TextButton(onClick = {
                    showDate = false
                    if (includeTime) {
                        showTime = true
                    } else {
                        commit(datePickerState.selectedDateMillis, includeTime = false, hour = 0, minute = 0, onChange = onChange)
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    reset()
                    showDate = false
                }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTime) {
        TimePickerDialog(
            title = { Text("Pick time") },
            onDismissRequest = {
                reset()
                showTime = false
            },
            confirmButton = {
                TextButton(onClick = {
                    commit(
                        dayMillisUtc = datePickerState.selectedDateMillis,
                        includeTime = true,
                        hour = timePickerState.hour,
                        minute = timePickerState.minute,
                        onChange = onChange,
                    )
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    reset()
                    showTime = false
                }) { Text("Cancel") }
            },
        ) {
            TimePicker(state = timePickerState)
        }
    }
}

private fun commit(
    dayMillisUtc: Long?,
    includeTime: Boolean,
    hour: Int,
    minute: Int,
    onChange: (Long) -> Unit,
) {
    if (dayMillisUtc == null) return
    val zone = ZoneId.systemDefault()
    val localDate =
        Instant
            .ofEpochMilli(dayMillisUtc)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
    val picked =
        if (includeTime) {
            localDate.atTime(hour, minute).atZone(zone).toEpochSecond()
        } else {
            // For date-only events, anchor to midnight in the user's local zone so the day
            // boundary matches their wall-clock intent.
            localDate.atStartOfDay(zone).toEpochSecond()
        }
    onChange(picked)
}

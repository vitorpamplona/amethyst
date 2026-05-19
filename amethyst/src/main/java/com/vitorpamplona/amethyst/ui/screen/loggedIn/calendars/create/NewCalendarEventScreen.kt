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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.SavingTopBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewCalendarEventScreen(
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    val vm: NewCalendarEventViewModel = viewModel()
    vm.init(accountViewModel)

    Scaffold(
        topBar = {
            SavingTopBar(
                titleRes = R.string.new_calendar_event,
                onCancel = { nav.popBack() },
                onPost = {
                    accountViewModel.launchSigner {
                        if (vm.publish()) {
                            nav.popBack()
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(
            modifier =
                Modifier
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = pad.calculateTopPadding(),
                        bottom = pad.calculateBottomPadding(),
                    ).consumeWindowInsets(pad)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AllDayToggleRow(vm)

            OutlinedTextField(
                value = vm.title.value,
                onValueChange = { vm.title.value = it },
                label = { Text(stringRes(R.string.calendar_event_title)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            val isAllDay by vm.isAllDay
            FieldLabel(stringRes(R.string.calendar_event_start))
            CalendarDateTimePickerButton(
                unixSeconds = vm.startSeconds.value,
                placeholder = stringRes(R.string.calendar_event_pick_date),
                includeTime = !isAllDay,
                onChange = { vm.startSeconds.value = it },
            )

            FieldLabel(stringRes(R.string.calendar_event_end))
            CalendarDateTimePickerButton(
                unixSeconds = vm.endSeconds.value,
                placeholder = stringRes(R.string.calendar_event_pick_date),
                includeTime = !isAllDay,
                onChange = { vm.endSeconds.value = it },
            )

            OutlinedTextField(
                value = vm.location.value,
                onValueChange = { vm.location.value = it },
                label = { Text(stringRes(R.string.calendar_event_location)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = vm.summary.value,
                onValueChange = { vm.summary.value = it },
                label = { Text(stringRes(R.string.calendar_event_summary)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            OutlinedTextField(
                value = vm.imageUrl.value,
                onValueChange = { vm.imageUrl.value = it },
                label = { Text(stringRes(R.string.calendar_event_image)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = vm.hashtags.value,
                onValueChange = { vm.hashtags.value = it },
                label = { Text(stringRes(R.string.calendar_event_hashtags)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            if (!vm.isValid()) {
                Text(
                    text = stringRes(R.string.calendar_event_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (!vm.isEndAfterStart()) {
                Text(
                    text = stringRes(R.string.calendar_event_end_before_start),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AllDayToggleRow(vm: NewCalendarEventViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringRes(R.string.calendar_event_all_day),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = vm.isAllDay.value,
            onCheckedChange = { vm.isAllDay.value = it },
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp),
    )
}

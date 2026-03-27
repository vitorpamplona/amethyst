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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.vanish

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.components.TextSpinner
import com.vitorpamplona.amethyst.ui.components.TitleExplainer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.toImmutableList
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestToVanishScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val connectedRelays by accountViewModel.account.client
        .connectedRelaysFlow()
        .collectAsStateWithLifecycle()

    var selectedRelayUrl by remember { mutableStateOf<String?>(null) }
    var allRelaysSelected by remember { mutableStateOf(false) }
    var vanishDate by remember { mutableLongStateOf(TimeUtils.now()) }
    var reason by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = vanishDate * 1000,
        )

    val currentTime = Instant.ofEpochMilli(vanishDate * 1000).atZone(ZoneId.systemDefault()).toLocalDateTime()

    val timePickerState =
        rememberTimePickerState(
            initialHour = currentTime.hour,
            initialMinute = currentTime.minute,
            is24Hour = false,
        )

    val relayOptions =
        remember(connectedRelays) {
            connectedRelays
                .sortedBy { it.url }
                .map { relay ->
                    TitleExplainer(relay.displayUrl(), relay.url)
                }.toImmutableList()
        }

    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = R.string.request_to_vanish), nav::popBack)
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Description
            Text(
                text = stringRes(R.string.request_to_vanish_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Relay Selection
            Text(
                text = stringRes(R.string.vanish_target_relay),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextSpinner(
                label = stringRes(R.string.vanish_target_relay),
                placeholder =
                    if (allRelaysSelected) {
                        stringRes(R.string.vanish_all_relays)
                    } else {
                        selectedRelayUrl ?: stringRes(R.string.vanish_select_relay)
                    },
                options = relayOptions,
                onSelect = { index ->
                    selectedRelayUrl = relayOptions[index].explainer
                    allRelaysSelected = false
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ALL RELAYS checkbox
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = allRelaysSelected,
                    onCheckedChange = {
                        allRelaysSelected = it
                        if (it) selectedRelayUrl = null
                    },
                )
                Text(
                    text = stringRes(R.string.vanish_all_relays),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (allRelaysSelected) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.error,
                                shape = RoundedCornerShape(8.dp),
                            ).padding(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringRes(R.string.vanish_all_relays_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            HorizontalDivider(thickness = DividerThickness)

            Spacer(modifier = Modifier.height(20.dp))

            // Date Picker
            Text(
                text = stringRes(R.string.vanish_date_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringRes(R.string.vanish_date_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedCard(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.CalendarMonth,
                        contentDescription = stringRes(R.string.vanish_select_date),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = formatTimestamp(vanishDate),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            HorizontalDivider(thickness = DividerThickness)

            Spacer(modifier = Modifier.height(20.dp))

            // Reason
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text(stringRes(R.string.vanish_reason_label)) },
                placeholder = { Text(stringRes(R.string.vanish_reason_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Send button
            Button(
                onClick = { showConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = allRelaysSelected || selectedRelayUrl != null,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Icon(
                    Icons.Outlined.DeleteForever,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringRes(R.string.vanish_send_request))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    showTimePicker = true
                }) { Text(stringRes(R.string.next)) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            title = {
                Text(stringRes(R.string.vanish_select_time))
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
                            } ?: TimeUtils.now()

                        val offset: ZoneOffset = ZoneId.systemDefault().rules.getOffset(Instant.now())

                        vanishDate = datetimeLocalTimeZone - offset.totalSeconds

                        showTimePicker = false
                    },
                ) { Text(stringRes(R.string.confirm)) }
            },
        ) {
            TimePicker(state = timePickerState)
        }
    }

    if (showConfirmDialog) {
        ConfirmVanishDialog(
            isAllRelays = allRelaysSelected,
            relayUrl = selectedRelayUrl,
            onConfirm = {
                showConfirmDialog = false
                if (allRelaysSelected) {
                    accountViewModel.requestToVanishFromEverywhere(reason, vanishDate)
                } else {
                    selectedRelayUrl?.let {
                        accountViewModel.requestToVanish(it, reason, vanishDate)
                    }
                }
                accountViewModel.toastManager.toast(
                    R.string.request_to_vanish,
                    R.string.vanish_request_sent,
                )
                nav.popBack()
            },
            onDismiss = { showConfirmDialog = false },
        )
    }
}

@Composable
private fun ConfirmVanishDialog(
    isAllRelays: Boolean,
    relayUrl: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                text = stringRes(R.string.vanish_confirm_title),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                text =
                    if (isAllRelays) {
                        stringRes(R.string.vanish_confirm_all_relays)
                    } else {
                        stringRes(R.string.vanish_confirm_single_relay, relayUrl ?: "")
                    },
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text(stringRes(R.string.vanish_send_request))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.cancel))
            }
        },
    )
}

private fun formatTimestamp(epochSeconds: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy  hh:mm a", Locale.getDefault())
    return sdf.format(Date(epochSeconds * 1000))
}

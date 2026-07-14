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
package com.vitorpamplona.amethyst.service.resourceusage

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.collectMemorySnapshot
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.crashreports.DEV_REPORT_PUBKEY
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeToMessage
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On app open, checks the resource-usage ledger against the
 * [ResourceUsageAlerts] thresholds and — at most once per week, unless the
 * user opted out — asks whether they'd like to review + send a usage report
 * to the developers. Confirming only PREFILLS the NIP-17 DM composer (crash
 * report pattern): the full report text is visible there and nothing is sent
 * until the user taps Send.
 */
@Composable
fun DisplayResourceUsageAlert(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val context = LocalContext.current
    val alert = remember { mutableStateOf<ResourceUsageAlerts.Alert?>(null) }

    LaunchedEffect(accountViewModel) {
        withContext(Dispatchers.IO) {
            val store = Amethyst.instance.resourceUsageStore
            val accountant = Amethyst.instance.resourceUsage
            if (!ResourceUsageAlerts.shouldPrompt(store.lastAlertAtSec(), store.alertsOptOut(), TimeUtils.now())) {
                return@withContext
            }
            val found = ResourceUsageAlerts.evaluate(accountant.allDaysIncludingLive(), accountant.today())
            if (found != null) {
                alert.value = found
            }
        }
    }

    // The 7-day rate limit is consumed when the user ACTS on the dialog (any
    // button or dismissal), not when it is shown: marking before the first
    // frame let a config change or process death burn the whole prompt budget
    // on a dialog nobody ever saw, silencing an active battery problem for a
    // week. Re-showing after an unhandled recreation is exactly what we want.
    val dismiss = {
        accountViewModel.runOnIO {
            Amethyst.instance.resourceUsageStore.markAlertPrompted(TimeUtils.now())
        }
        alert.value = null
    }

    alert.value?.let { found ->
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringRes(R.string.resource_usage_alert_title)) },
            text = {
                Text(
                    stringRes(
                        R.string.resource_usage_alert_message,
                        reasonDescription(found),
                    ),
                )
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        accountViewModel.runOnIO {
                            Amethyst.instance.resourceUsageStore.setAlertsOptOut(true)
                        }
                        dismiss()
                    }) {
                        Text(stringRes(R.string.resource_usage_alert_opt_out))
                    }
                    TextButton(onClick = dismiss) {
                        Text(stringRes(R.string.resource_usage_alert_not_now))
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    nav.nav {
                        val report =
                            withContext(Dispatchers.IO) {
                                ResourceUsageReportAssembler().buildReport(
                                    Amethyst.instance.resourceUsage.allDaysIncludingLive(),
                                    Amethyst.instance.resourceUsage.today(),
                                    collectMemorySnapshot(context),
                                )
                            }
                        routeToMessage(
                            user = LocalCache.getOrCreateUser(DEV_REPORT_PUBKEY),
                            draftMessage = report,
                            accountViewModel = accountViewModel,
                            expiresDays = 30,
                        )
                    }
                    dismiss()
                }) {
                    Text(stringRes(R.string.resource_usage_alert_send))
                }
            },
        )
    }
}

@Composable
private fun reasonDescription(alert: ResourceUsageAlerts.Alert): String =
    when (alert.reason) {
        ResourceUsageAlerts.Reason.BACKGROUND_MOBILE_DATA ->
            stringRes(
                R.string.resource_usage_reason_bg_data,
                ResourceUsageReportAssembler.formatBytes(alert.value),
            )

        ResourceUsageAlerts.Reason.BACKGROUND_MOBILE_CONNECTION_TIME ->
            stringRes(
                R.string.resource_usage_reason_conn_time,
                ResourceUsageReportAssembler.formatConnHours(alert.value),
            )

        ResourceUsageAlerts.Reason.WAKELOCK_TIME ->
            stringRes(
                R.string.resource_usage_reason_wakelock,
                ResourceUsageReportAssembler.formatDurationMs(alert.value),
            )

        ResourceUsageAlerts.Reason.PROCESS_CHURN ->
            pluralStringResource(
                R.plurals.resource_usage_reason_churn,
                alert.value.toInt(),
                alert.value.toInt(),
            )

        ResourceUsageAlerts.Reason.RECONNECT_CHURN ->
            pluralStringResource(
                R.plurals.resource_usage_reason_reconnects,
                alert.value.toInt(),
                alert.value.toInt(),
            )
    }

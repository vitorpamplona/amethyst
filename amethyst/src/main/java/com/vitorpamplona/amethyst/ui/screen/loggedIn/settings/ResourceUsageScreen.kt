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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.MemorySnapshot
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.collectMemorySnapshot
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.resourceusage.DEV_REPORT_PUBKEY
import com.vitorpamplona.amethyst.service.resourceusage.ResourceUsageReportAssembler
import com.vitorpamplona.amethyst.service.resourceusage.ResourceUsageReportAssembler.Companion.formatBytes
import com.vitorpamplona.amethyst.service.resourceusage.ResourceUsageReportAssembler.Companion.formatConnHours
import com.vitorpamplona.amethyst.service.resourceusage.ResourceUsageReportAssembler.Companion.formatDurationMs
import com.vitorpamplona.amethyst.service.resourceusage.UsageSummary
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeToMessage
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The resource-usage ledger: how much network, relay connection time, and
 * background activity the app consumed, per subsystem — with an explicit,
 * user-initiated path to DM the numbers to the developers (same NIP-17 flow
 * as crash reports). Everything on this screen stays on-device until the
 * user sends that DM.
 */
@Composable
fun ResourceUsageScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var days by remember { mutableStateOf<Map<Long, Map<String, Long>>?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            days = Amethyst.instance.resourceUsage.allDaysIncludingLive()
        }
    }

    // Live memory snapshot, refreshed only while this screen is composed.
    // Collected off-main: DiskLruCache.size() contends with journal I/O.
    val context = LocalContext.current
    val memory by produceState<MemorySnapshot?>(null) {
        while (true) {
            value = withContext(Dispatchers.IO) { collectMemorySnapshot(context) }
            delay(2_000)
        }
    }

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(id = R.string.resource_usage_title), nav) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val loaded = days
            if (loaded == null) {
                Text(
                    text = stringRes(R.string.resource_usage_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val today = Amethyst.instance.resourceUsage.today()
                val todaySummary = UsageSummary.from(loaded[today].orEmpty())
                val weekSummary = UsageSummary.fromDays((today - 6..today).mapNotNull { loaded[it] })

                UsageSummarySection(R.string.resource_usage_today, todaySummary)
                UsageSummarySection(R.string.resource_usage_week, weekSummary)
                SubsystemSection(weekSummary)
                MemorySection(memory)
                SendReportSection(accountViewModel, nav, loaded, today, memory)
            }
        }
    }
}

@Composable
private fun UsageSummarySection(
    @StringRes title: Int,
    s: UsageSummary,
) {
    SettingsSection(title) {
        MetricRow(R.string.resource_usage_cellular_bg, formatBytes(s.mobileBytesBg))
        SettingsDivider()
        MetricRow(R.string.resource_usage_cellular_fg, formatBytes(s.mobileBytesFg))
        SettingsDivider()
        MetricRow(R.string.resource_usage_wifi, formatBytes(s.wifiBytesBg + s.wifiBytesFg))
        SettingsDivider()
        MetricRow(R.string.resource_usage_relay_time, formatConnHours(s.relayConnMs))
        SettingsDivider()
        MetricRow(R.string.resource_usage_relay_time_bg_mobile, formatConnHours(s.relayConnMsMobileBg))
        SettingsDivider()
        MetricRow(R.string.resource_usage_wakelock, formatDurationMs(s.wakelockMs))
        SettingsDivider()
        MetricRow(R.string.resource_usage_reconnects, "${s.relayConnects} (${s.relayConnectFails})")
        SettingsDivider()
        MetricRow(R.string.resource_usage_verifies, s.verifyCount.toString())
        SettingsDivider()
        MetricRow(R.string.resource_usage_cpu, formatDurationMs(s.cpuMs))
        SettingsDivider()
        MetricRow(R.string.resource_usage_fg_time, formatDurationMs(s.foregroundMs))
        SettingsDivider()
        MetricRow(R.string.resource_usage_worker_runs, s.workerRuns.toString())
        SettingsDivider()
        MetricRow(R.string.resource_usage_app_starts, s.appStarts.toString())
    }
}

@Composable
private fun SubsystemSection(week: UsageSummary) {
    if (week.bytesPerSubsystem.isEmpty()) return
    SettingsSection(R.string.resource_usage_by_subsystem) {
        val rows = week.bytesPerSubsystem.entries.sortedByDescending { it.value }
        rows.forEachIndexed { index, (subsystem, bytes) ->
            if (index > 0) SettingsDivider()
            MetricRow(subsystemLabel(subsystem), formatBytes(bytes))
        }
    }
}

@StringRes
private fun subsystemLabel(subsystem: String): Int =
    when (subsystem) {
        "relay" -> R.string.resource_usage_subsystem_relay
        "image" -> R.string.resource_usage_subsystem_image
        "video" -> R.string.resource_usage_subsystem_video
        "uploads" -> R.string.resource_usage_subsystem_uploads
        "money" -> R.string.resource_usage_subsystem_money
        "nip05" -> R.string.resource_usage_subsystem_nip05
        "preview" -> R.string.resource_usage_subsystem_preview
        "push" -> R.string.resource_usage_subsystem_push
        else -> R.string.resource_usage_subsystem_other
    }

@Composable
private fun MetricRow(
    @StringRes label: Int,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringRes(label),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MemorySection(memory: MemorySnapshot?) {
    if (memory == null) return
    SettingsSection(R.string.resource_usage_memory_section) {
        MetricRow(R.string.resource_usage_memory_heap, "${memory.heapUsedMb} / ${memory.heapMaxMb} MB")
        SettingsDivider()
        MetricRow(R.string.resource_usage_memory_native, "${memory.nativeHeapUsedMb} MB")
        SettingsDivider()
        MetricRow(R.string.resource_usage_memory_image_cache, "${memory.imageCacheUsedMb} / ${memory.imageCacheMaxMb} MB")
        SettingsDivider()
        MetricRow(R.string.resource_usage_memory_image_disk, "${memory.imageDiskUsedMb} / ${memory.imageDiskMaxMb} MB")
        SettingsDivider()
        MetricRow(R.string.resource_usage_memory_notes, memory.noteCount.toString())
        SettingsDivider()
        MetricRow(R.string.resource_usage_memory_users, memory.userCount.toString())
        SettingsDivider()
        MetricRow(R.string.resource_usage_memory_addressables, memory.addressableCount.toString())
        SettingsDivider()
        MetricRow(R.string.resource_usage_memory_chatrooms, memory.chatroomCount.toString())
        SettingsDivider()
        MetricRow(R.string.resource_usage_memory_device_class, "${memory.memoryClassMb} MB")
    }
}

@Composable
private fun SendReportSection(
    accountViewModel: AccountViewModel,
    nav: INav,
    days: Map<Long, Map<String, Long>>,
    today: Long,
    memory: MemorySnapshot?,
) {
    SettingsSection(R.string.resource_usage_send_section) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringRes(R.string.resource_usage_send_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    val report = ResourceUsageReportAssembler().buildReport(days, today, memory)
                    nav.nav {
                        routeToMessage(
                            user = LocalCache.getOrCreateUser(DEV_REPORT_PUBKEY),
                            draftMessage = report,
                            accountViewModel = accountViewModel,
                            expiresDays = 30,
                        )
                    }
                },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringRes(R.string.resource_usage_send_button))
            }
        }
    }
}

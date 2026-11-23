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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.marmotDevices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.marmot.devices.DeviceActionFailure
import com.vitorpamplona.amethyst.commons.marmot.devices.MarmotDevicesViewModel
import com.vitorpamplona.amethyst.commons.model.marmot.devices.LinkedDevice
import com.vitorpamplona.amethyst.commons.model.marmot.devices.MarmotDevicesSnapshot
import com.vitorpamplona.amethyst.commons.model.marmot.devices.SignIn
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_explainer
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_failed_action
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_failed_switch
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_history_note
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_last_active
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_link_new
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_link_new_desc
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_link_this
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_link_this_desc
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_linked_at
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_needs_signer
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_not_linked
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_remaining_chats
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_remove_body
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_remove_title
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_restart_off
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_restart_on
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_section_link
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_section_roster
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_section_sign_ins
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_section_switch
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_sign_in_earlier
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_sign_in_new
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_sign_in_rejected
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_sign_in_removed
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_status_inactive
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_status_leaving
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_status_moved
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_switch_desc
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_switch_title
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_this_device
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_title
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_unavailable
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_unlinked_body
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_unlinked_title
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_unnamed
import com.vitorpamplona.amethyst.commons.resources.marmot_sign_in_not_me
import com.vitorpamplona.amethyst.commons.resources.remove
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.timeAgoNoDot
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsControlRow
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsDivider
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsSection
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsSwitchTile
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.marmot.appComponents.DeviceStatusV1
import com.vitorpamplona.marmot.features.deviceGroup.MarmotDeviceGroupManager
import org.jetbrains.compose.resources.StringResource

/** Everything the devices screen draws, so a preview or a test can hand it a made-up state. */
data class MarmotDevicesScreenModel(
    val switchAvailable: Boolean,
    val switchOn: Boolean,
    val runtimeOn: Boolean,
    val canSign: Boolean,
    val snapshot: MarmotDevicesSnapshot,
)

/**
 * This account's Marmot devices: the device-group switch, then (while device groups run) the
 * roster, the two ways to link, and the sign-ins this device does not know.
 */
@Composable
fun MarmotDevicesScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: MarmotDevicesViewModel =
        viewModel(
            key = "MarmotDevices" + accountViewModel.account.signer.pubKey,
            factory = MarmotDevicesViewModel.Factory(accountViewModel.account),
        )
    val switchOn by viewModel.switchOn.collectAsStateWithLifecycle()
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.marmot_devices_title), nav) },
    ) { padding ->
        MarmotDevicesContent(
            model = MarmotDevicesScreenModel(viewModel.switchAvailable, switchOn, viewModel.runtimeOn, viewModel.canSign, snapshot),
            onSwitch = viewModel::setSwitch,
            onLinkNewDevice = { nav.nav(Route.MarmotDeviceLink(asNewDevice = false)) },
            onLinkThisDevice = { nav.nav(Route.MarmotDeviceLink(asNewDevice = true)) },
            onRemove = { viewModel.removeDevice(it.slot) },
            onRejectSignIn = { viewModel.rejectSignIn(it.slot) },
            modifier = Modifier.padding(padding),
        )
    }

    failure?.let {
        AlertDialog(
            onDismissRequest = viewModel::clearFailure,
            text = { Text(stringRes(if (it == DeviceActionFailure.SWITCH) Res.string.marmot_devices_failed_switch else Res.string.marmot_devices_failed_action)) },
            confirmButton = { TextButton(onClick = viewModel::clearFailure) { Text(stringRes(Res.string.cancel)) } },
        )
    }
}

@Composable
fun MarmotDevicesContent(
    model: MarmotDevicesScreenModel,
    onSwitch: (Boolean) -> Unit,
    onLinkNewDevice: () -> Unit,
    onLinkThisDevice: () -> Unit,
    onRemove: (LinkedDevice) -> Unit,
    onRejectSignIn: (SignIn) -> Unit,
    modifier: Modifier = Modifier,
) {
    var removing by remember { mutableStateOf<LinkedDevice?>(null) }
    val snapshot = model.snapshot

    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Explainer(stringRes(Res.string.marmot_devices_explainer))

        if (model.switchAvailable) {
            SettingsSection(Res.string.marmot_devices_section_switch) {
                SettingsSwitchTile(
                    icon = MaterialSymbols.PhonelinkLock,
                    title = Res.string.marmot_devices_switch_title,
                    description = Res.string.marmot_devices_switch_desc,
                    checked = model.switchOn,
                    onCheckedChange = onSwitch,
                )
                if (model.switchOn != model.runtimeOn) {
                    SettingsDivider()
                    NoticeRow(
                        MaterialSymbols.Info,
                        stringRes(if (model.switchOn) Res.string.marmot_devices_restart_on else Res.string.marmot_devices_restart_off),
                    )
                }
            }
        }

        if (!model.runtimeOn) {
            Explainer(stringRes(Res.string.marmot_devices_history_note))
            if (!model.switchAvailable) Explainer(stringRes(Res.string.marmot_devices_unavailable))
            return@Column
        }

        if (snapshot.unlinked) {
            Banner(
                symbol = MaterialSymbols.Warning,
                title = stringRes(Res.string.marmot_devices_unlinked_title),
                body = stringRes(Res.string.marmot_devices_unlinked_body),
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
            )
        }

        snapshot.remainingChats?.takeIf { it > 0 }?.let { remaining ->
            Banner(
                symbol = MaterialSymbols.Sync,
                title = pluralStringRes(Res.plurals.marmot_devices_remaining_chats, remaining.toInt(), remaining.toInt()),
                body = null,
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }

        SettingsSection(Res.string.marmot_devices_section_roster) {
            if (snapshot.devices.isEmpty()) {
                NoticeRow(MaterialSymbols.Info, stringRes(Res.string.marmot_devices_not_linked))
            }
            snapshot.devices.forEachIndexed { index, device ->
                if (index > 0) SettingsDivider()
                DeviceRow(device, onRemove = { removing = device })
            }
        }

        if (!snapshot.unlinked) {
            SettingsSection(Res.string.marmot_devices_section_link) {
                if (!model.canSign) {
                    NoticeRow(MaterialSymbols.Lock, stringRes(Res.string.marmot_devices_needs_signer))
                } else {
                    LinkEntry(MaterialSymbols.Link, Res.string.marmot_devices_link_new, Res.string.marmot_devices_link_new_desc, onLinkNewDevice)
                    if (!snapshot.linked) {
                        SettingsDivider()
                        LinkEntry(MaterialSymbols.PhonelinkLock, Res.string.marmot_devices_link_this, Res.string.marmot_devices_link_this_desc, onLinkThisDevice)
                    }
                }
            }
        }

        if (snapshot.signIns.isNotEmpty()) {
            SettingsSection(Res.string.marmot_devices_section_sign_ins) {
                snapshot.signIns.sortedByDescending { it.publishedAt }.forEachIndexed { index, signIn ->
                    if (index > 0) SettingsDivider()
                    SignInRow(signIn, onRejectSignIn)
                }
            }
        }

        Explainer(stringRes(Res.string.marmot_devices_history_note))
    }

    removing?.let { device ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringRes(Res.string.marmot_devices_remove_title, deviceName(device))) },
            text = { Text(stringRes(Res.string.marmot_devices_remove_body)) },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    onRemove(device)
                }) { Text(stringRes(Res.string.remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringRes(Res.string.cancel)) } },
        )
    }
}

@Composable
private fun deviceName(device: LinkedDevice): String = device.label.ifBlank { stringRes(Res.string.marmot_devices_unnamed) }

@Composable
private fun DeviceRow(
    device: LinkedDevice,
    onRemove: () -> Unit,
) {
    val details =
        buildList {
            listOf(device.client, device.platform)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .takeIf { it.isNotEmpty() }
                ?.let(::add)
            statusLabel(device.status)?.let { add(stringRes(it)) }
            if (device.lastActiveAt != null) {
                add(stringRes(Res.string.marmot_devices_last_active, timeAgoNoDot(device.lastActiveAt)))
            } else if (device.linkedAt > 0) {
                add(stringRes(Res.string.marmot_devices_linked_at, timeAgoNoDot(device.linkedAt)))
            }
        }.joinToString(" · ")

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBox(MaterialSymbols.PhonelinkLock, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(deviceName(device), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
                if (device.isThisDevice) {
                    Text(
                        text = stringRes(Res.string.marmot_devices_this_device),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier =
                            Modifier
                                .padding(start = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!device.isThisDevice) {
            IconButton(onClick = onRemove) {
                Icon(
                    symbol = MaterialSymbols.Delete,
                    contentDescription = stringRes(Res.string.remove),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun statusLabel(status: DeviceStatusV1): StringResource? =
    when (status) {
        DeviceStatusV1.ACTIVE -> null
        DeviceStatusV1.INACTIVE -> Res.string.marmot_devices_status_inactive
        DeviceStatusV1.LEAVING -> Res.string.marmot_devices_status_leaving
        DeviceStatusV1.MOVED -> Res.string.marmot_devices_status_moved
    }

@Composable
private fun SignInRow(
    signIn: SignIn,
    onReject: (SignIn) -> Unit,
) {
    val time = timeAgoNoDot(signIn.publishedAt)
    val text =
        when (signIn.kind) {
            MarmotDeviceGroupManager.SignInClass.ALREADY_SIGNED_IN -> stringRes(Res.string.marmot_devices_sign_in_earlier, time)
            MarmotDeviceGroupManager.SignInClass.REJECTED -> stringRes(Res.string.marmot_devices_sign_in_rejected, time)
            MarmotDeviceGroupManager.SignInClass.REMOVED -> stringRes(Res.string.marmot_devices_sign_in_removed, time)
            else -> stringRes(Res.string.marmot_devices_sign_in_new, time)
        }
    val askable = signIn.kind == MarmotDeviceGroupManager.SignInClass.NEW_SIGN_IN || signIn.kind == MarmotDeviceGroupManager.SignInClass.ALREADY_SIGNED_IN
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBox(MaterialSymbols.Security, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 12.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Text(signIn.slot.take(12), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (askable) {
            TextButton(onClick = { onReject(signIn) }) { Text(stringRes(Res.string.marmot_sign_in_not_me), color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun LinkEntry(
    icon: MaterialSymbol,
    title: StringResource,
    description: StringResource,
    onClick: () -> Unit,
) {
    SettingsControlRow(icon = icon, title = stringRes(title), description = stringRes(description), onClick = onClick) {
        Icon(
            symbol = MaterialSymbols.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoticeRow(
    symbol: MaterialSymbol,
    text: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(symbol = symbol, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun Explainer(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
internal fun Banner(
    symbol: MaterialSymbol,
    title: String,
    body: String?,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(container)
                .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(symbol = symbol, contentDescription = null, modifier = Modifier.size(22.dp), tint = content)
        Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = content)
            body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = content) }
        }
    }
}

@Composable
private fun IconBox(
    symbol: MaterialSymbol,
    container: Color,
    tint: Color,
) {
    Box(
        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(symbol = symbol, contentDescription = null, modifier = Modifier.size(20.dp), tint = tint)
    }
}

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
package com.vitorpamplona.amethyst.ui.actions.mediaServers

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.components.util.setText
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.quartz.nip56Reports.ReportType
import kotlinx.coroutines.launch

@Composable
fun BlossomBlobManagerScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val vm: BlossomBlobManagerViewModel = viewModel()
    vm.init(accountViewModel)

    LaunchedEffect(accountViewModel) { vm.refresh() }

    val blobs by vm.blobs.collectAsStateWithLifecycle()
    val loading by vm.isLoading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val pendingPayment by vm.pendingPayment.collectAsStateWithLifecycle()

    pendingPayment?.let { pending ->
        BlossomPaymentDialog(
            host = pending.targetHost,
            amountSats = pending.amountSats,
            reason = pending.payment.sanitizedReason(),
            onConfirm = { vm.confirmPendingPayment() },
            onDismiss = { vm.cancelPendingPayment() },
        )
    }

    Scaffold(
        topBar = {
            TopBarExtensibleWithBackButton(
                title = { Text(stringRes(R.string.my_blossom_data)) },
                showBackButton = nav.canPop(),
                popBack = { nav.popBack() },
                actions = {
                    IconButton(onClick = { vm.refresh() }, enabled = !loading) {
                        if (loading) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(symbol = MaterialSymbols.Sync, contentDescription = stringRes(R.string.blossom_refresh))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding(),
                    ),
        ) {
            when {
                loading && blobs.isEmpty() -> CenteredState { CircularProgressIndicator() }

                error != null && blobs.isEmpty() ->
                    CenteredState {
                        StatusGlyph(MaterialSymbols.Warning, MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Text(error ?: "", color = MaterialTheme.colorScheme.grayText)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { vm.refresh() }) { Text(stringRes(R.string.retry)) }
                    }

                blobs.isEmpty() ->
                    CenteredState {
                        StatusGlyph(MaterialSymbols.Storage, MaterialTheme.colorScheme.grayText)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringRes(R.string.manage_stored_files_empty),
                            color = MaterialTheme.colorScheme.grayText,
                        )
                    }

                else ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (blobs.any { it.hasMissing }) {
                            item { SyncAllBanner(onSyncAll = { vm.syncAll() }) }
                        }
                        items(blobs, key = { it.hash }) { row ->
                            BlobCard(row, vm)
                        }
                    }
            }
        }
    }
}

@Composable
private fun CenteredState(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun StatusGlyph(
    symbol: MaterialSymbol,
    tint: Color,
) {
    Box(
        modifier =
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(symbol = symbol, contentDescription = null, modifier = Modifier.size(34.dp), tint = tint)
    }
}

@Composable
private fun SyncAllBanner(onSyncAll: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(stringRes(R.string.blossom_sync_gaps), style = MaterialTheme.typography.bodyMedium)
        }
        FilledTonalButton(onClick = onSyncAll) {
            Icon(symbol = MaterialSymbols.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringRes(R.string.blossom_sync_all))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlobCard(
    row: BlobRow,
    vm: BlossomBlobManagerViewModel,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var reportOpen by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header: thumbnail / file glyph + hash + overflow menu.
        Row(verticalAlignment = Alignment.CenterVertically) {
            BlobThumbnail(row)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = row.hash.take(12) + "…" + row.hash.takeLast(6),
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
                Text(
                    text = listOfNotNull(row.type, row.size?.let { humanBytes(it) }).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                )
            }

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(symbol = MaterialSymbols.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (row.url != null) {
                        DropdownMenuItem(
                            text = { Text(stringRes(R.string.copy)) },
                            leadingIcon = { MenuIcon(MaterialSymbols.ContentCopy) },
                            onClick = {
                                menuOpen = false
                                val url = row.url
                                scope.launch { clipboard.setText(url) }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringRes(R.string.blossom_open)) },
                            leadingIcon = { MenuIcon(MaterialSymbols.AutoMirrored.OpenInNew) },
                            onClick = {
                                menuOpen = false
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, row.url.toUri())) }
                            },
                        )
                    }
                    if (row.hasPresent) {
                        DropdownMenuItem(
                            text = { Text(stringRes(R.string.blossom_report)) },
                            leadingIcon = { MenuIcon(MaterialSymbols.Report) },
                            onClick = {
                                menuOpen = false
                                reportOpen = true
                            },
                        )
                        HorizontalDivider()
                        row.presentServers.forEach { server ->
                            DropdownMenuItem(
                                text = { Text(stringRes(R.string.blossom_delete_from_host, vm.hostOf(server))) },
                                leadingIcon = { MenuIcon(MaterialSymbols.Delete, MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuOpen = false
                                    vm.delete(row.hash, server)
                                },
                            )
                        }
                    }
                }
            }
        }

        // Per-server presence pills (green = has it, grey = missing, spinner = working).
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            row.servers.forEach { ServerPill(it) }
        }

        // Primary CTA: fill the gaps.
        if (row.hasMissing && row.url != null) {
            FilledTonalButton(
                onClick = { vm.mirrorToMissing(row) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(symbol = MaterialSymbols.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringRes(R.string.blossom_mirror_to_missing))
            }
        }
    }

    if (reportOpen) {
        BlossomReportDialog(row = row, vm = vm, onDismiss = { reportOpen = false })
    }
}

@Composable
private fun BlobThumbnail(row: BlobRow) {
    val shape = RoundedCornerShape(12.dp)
    if (row.url != null && row.type?.startsWith("image/") == true) {
        AsyncImage(
            model = row.url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(shape),
        )
    } else {
        Box(
            modifier = Modifier.size(48.dp).clip(shape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            val glyph = if (row.type?.startsWith("video/") == true) MaterialSymbols.Download else MaterialSymbols.Storage
            Icon(
                symbol = glyph,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun ServerPill(presence: ServerPresence) {
    val present = presence.state == PresenceState.PRESENT
    val pending = presence.state == PresenceState.PENDING
    val accent = MaterialTheme.colorScheme.allGoodColor
    val bg =
        if (present) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier = Modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (pending) {
            CircularProgressIndicator(
                modifier = Modifier.size(9.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            val dot = if (present) accent else MaterialTheme.colorScheme.grayText
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(dot))
        }
        Text(
            text = presence.host,
            style = MaterialTheme.typography.labelMedium,
            color = if (present) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.grayText,
        )
    }
}

@Composable
private fun MenuIcon(
    symbol: MaterialSymbol,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Icon(symbol = symbol, contentDescription = null, modifier = Modifier.size(20.dp), tint = tint)
}

private fun humanBytes(bytes: Long): String =
    when {
        bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
        bytes >= 1_000 -> "${bytes / 1_000} KB"
        else -> "$bytes B"
    }

@Composable
private fun BlossomPaymentDialog(
    host: String,
    amountSats: Long?,
    reason: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(symbol = MaterialSymbols.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.allGoodColor) },
        title = { Text(stringRes(R.string.blossom_payment_title)) },
        text = {
            Column {
                Text(text = stringRes(R.string.blossom_payment_message, host))
                // X-Reason is server-controlled: it is sanitized upstream and rendered
                // here attributed to the server, in a dimmer italic, so it can never be
                // mistaken for Amethyst's own wording (e.g. a fake "Pay 1 sat").
                reason?.let {
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = stringRes(R.string.blossom_payment_server_says, host, it),
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = onConfirm) {
                Icon(symbol = MaterialSymbols.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    if (amountSats != null) {
                        pluralStringResource(R.plurals.blossom_pay_sats, amountSats.toInt(), amountSats.toInt())
                    } else {
                        stringRes(R.string.blossom_pay)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(R.string.cancel)) }
        },
    )
}

@Composable
private fun BlossomReportDialog(
    row: BlobRow,
    vm: BlossomBlobManagerViewModel,
    onDismiss: () -> Unit,
) {
    var comment by remember { mutableStateOf("") }
    var typeMenuOpen by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf(ReportType.OTHER) }
    // Report to the first server that actually holds the blob.
    val server = row.presentServers.firstOrNull() ?: return

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(symbol = MaterialSymbols.Report, contentDescription = null) },
        title = { Text(stringRes(R.string.blossom_report_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    OutlinedButton(onClick = { typeMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(type.code)
                    }
                    DropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
                        ReportType.entries.forEach { rt ->
                            DropdownMenuItem(
                                text = { Text(rt.code) },
                                onClick = {
                                    type = rt
                                    typeMenuOpen = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text(stringRes(R.string.blossom_report_comment_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = {
                vm.report(row.hash, server, type, comment)
                onDismiss()
            }) { Text(stringRes(R.string.blossom_send)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(R.string.cancel)) }
        },
    )
}

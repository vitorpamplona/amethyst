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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.quartz.nip56Reports.ReportType

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
            amountSats = pending.amountSats,
            reason = pending.payment.reason,
            onConfirm = { vm.confirmPendingPayment() },
            onDismiss = { vm.cancelPendingPayment() },
        )
    }

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(R.string.manage_stored_files), nav) },
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.refresh() }) {
                Icon(
                    symbol = MaterialSymbols.Sync,
                    contentDescription = stringRes(R.string.retry),
                    modifier = Modifier.size(22.dp),
                )
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding(),
                    ),
        ) {
            when {
                loading && blobs.isEmpty() ->
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) { CircularProgressIndicator() }

                error != null && blobs.isEmpty() ->
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(error ?: "", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { vm.refresh() }) { Text(stringRes(R.string.retry)) }
                    }

                blobs.isEmpty() ->
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) { Text(stringRes(R.string.manage_stored_files_empty), color = MaterialTheme.colorScheme.grayText) }

                else ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(blobs, key = { it.hash }) { row ->
                            BlobCard(row, vm)
                        }
                    }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlobCard(
    row: BlobRow,
    vm: BlossomBlobManagerViewModel,
) {
    var deleteMenuOpen by remember { mutableStateOf(false) }
    var reportOpen by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (row.url != null && row.type?.startsWith("image/") == true) {
                    AsyncImage(
                        model = row.url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.hash.take(16) + "…",
                        style = MaterialTheme.typography.titleSmall,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                    )
                    Text(
                        text = listOfNotNull(row.type, row.size?.let { humanBytes(it) }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.grayText,
                    )
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.serversPresent.forEach { server ->
                    AssistChip(
                        onClick = {},
                        label = { Text(vm.hostOf(server), style = MaterialTheme.typography.labelSmall) },
                        colors =
                            AssistChipDefaults.assistChipColors(
                                labelColor = MaterialTheme.colorScheme.primary,
                            ),
                    )
                }
                row.serversMissing.forEach { server ->
                    AssistChip(
                        onClick = {},
                        label = { Text(vm.hostOf(server), style = MaterialTheme.typography.labelSmall) },
                        colors =
                            AssistChipDefaults.assistChipColors(
                                labelColor = MaterialTheme.colorScheme.grayText,
                            ),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (row.serversMissing.isNotEmpty() && row.url != null) {
                    OutlinedButton(onClick = { vm.mirrorToMissing(row) }) {
                        Text(stringRes(R.string.blossom_mirror_to_missing), style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (row.serversPresent.isNotEmpty()) {
                    Column {
                        OutlinedButton(onClick = { deleteMenuOpen = true }) {
                            Text(stringRes(R.string.blossom_delete_from), style = MaterialTheme.typography.labelMedium)
                        }
                        DropdownMenu(expanded = deleteMenuOpen, onDismissRequest = { deleteMenuOpen = false }) {
                            row.serversPresent.forEach { server ->
                                DropdownMenuItem(
                                    text = { Text(vm.hostOf(server)) },
                                    onClick = {
                                        deleteMenuOpen = false
                                        vm.delete(row.hash, server)
                                    },
                                )
                            }
                        }
                    }
                }

                if (row.serversPresent.isNotEmpty()) {
                    TextButton(onClick = { reportOpen = true }) {
                        Text(stringRes(R.string.blossom_report), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (row.url != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(row.url)) }) {
                        Text(stringRes(R.string.copy), style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, row.url.toUri())) }
                    }) {
                        Text(stringRes(R.string.blossom_open), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }

    if (reportOpen) {
        BlossomReportDialog(
            row = row,
            vm = vm,
            onDismiss = { reportOpen = false },
        )
    }
}

@Composable
private fun BlossomPaymentDialog(
    amountSats: Long?,
    reason: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.blossom_payment_title)) },
        text = {
            Text(
                text =
                    listOfNotNull(
                        stringRes(R.string.blossom_payment_message),
                        reason,
                    ).joinToString("\n\n"),
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
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

private fun humanBytes(bytes: Long): String =
    when {
        bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
        bytes >= 1_000 -> "${bytes / 1_000} KB"
        else -> "$bytes B"
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
    val server = row.serversPresent.firstOrNull() ?: return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.blossom_report_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column {
                    OutlinedButton(onClick = { typeMenuOpen = true }) { Text(type.code) }
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
            Button(onClick = {
                vm.report(row.hash, server, type, comment)
                onDismiss()
            }) { Text(stringRes(R.string.blossom_send)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(R.string.cancel)) }
        },
    )
}

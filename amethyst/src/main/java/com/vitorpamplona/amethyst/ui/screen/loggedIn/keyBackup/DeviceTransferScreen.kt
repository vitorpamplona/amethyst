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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.keyBackup

import android.content.Context
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.accountTransfer.AccountTransferService
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.account.transfer.AccountTransferBundle
import com.vitorpamplona.amethyst.commons.model.account.transfer.AccountTransferEnvelope
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.ButtonPadding
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Moves the parts of an account that relays never see to another device.
 *
 * Everything published under the user's key — profile, follows, relay lists,
 * mutes, and the NIP-78 settings blob — returns on its own when they log in on
 * the new phone. This screen is for the remainder: wallet connections, Cashu
 * counters, Tor settings, read markers, and (only if asked for) the secret key.
 * See `amethyst/plans/2026-08-21-account-migration-new-phone.md`.
 */
@Composable
fun DeviceTransferScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Same treatment as the key backup screen, and for the same reason: the
    // password typed here can be revealed on screen and is the only thing
    // protecting every secret key in the exported file. Cleared on dispose so
    // the flag never leaks to other screens.
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = context.getFragmentActivity()?.window
        window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(R.string.device_transfer), nav = nav) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringRes(R.string.device_transfer_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.grayText,
            )

            Spacer(Modifier.height(24.dp))
            ExportSection(accountViewModel)

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            ImportSection(accountViewModel)
        }
    }
}

@Composable
private fun ExportSection(accountViewModel: AccountViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    // The password is only in memory until the file lands, so the export runs
    // from inside the picker callback rather than being prepared up front.
    val saveLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(TRANSFER_MIME_TYPE)) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult

            busy = true
            scope.launch {
                try {
                    // Transient accounts are deliberately not persisted on this
                    // device; importing one would recreate it as a permanent,
                    // keyless entry in the account switcher.
                    val npubs = LocalPreferences.allSavedAccounts().filter { !it.isTransient }.map { it.npub }
                    val bytes = AccountTransferService.export(npubs, password)
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                            ?: error("Could not open the chosen file for writing")
                    }
                    password = ""
                    confirmation = ""
                    accountViewModel.toastManager.toast(R.string.device_transfer, R.string.device_transfer_export_done)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w("DeviceTransfer", "Could not write the transfer file", e)
                    accountViewModel.toastManager.toast(R.string.device_transfer, R.string.device_transfer_export_failed, e)
                } finally {
                    busy = false
                }
            }
        }

    SectionTitle(stringRes(R.string.device_transfer_export_title))

    Text(
        text = stringRes(R.string.device_transfer_export_explainer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.grayText,
    )

    Spacer(Modifier.height(12.dp))

    PasswordField(
        value = password,
        onValueChange = { password = it },
        placeholder = stringRes(R.string.device_transfer_password),
    )

    Spacer(Modifier.height(8.dp))

    PasswordField(
        value = confirmation,
        onValueChange = { confirmation = it },
        placeholder = stringRes(R.string.device_transfer_password_confirm),
    )

    // A typo in an export password is only discovered on the new phone, when the
    // old one may already be wiped — so mismatches are caught before writing.
    val mismatched = confirmation.isNotEmpty() && confirmation != password
    if (mismatched) {
        Text(
            text = stringRes(R.string.device_transfer_password_mismatch),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    Spacer(Modifier.height(12.dp))

    Text(
        text = stringRes(R.string.device_transfer_keys_warning),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )

    Spacer(Modifier.height(12.dp))

    Button(
        onClick = { saveLauncher.launch(defaultFileName()) },
        enabled = !busy && password.isNotEmpty() && password == confirmation,
        shape = ButtonBorder,
        contentPadding = ButtonPadding,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Icon(
            symbol = MaterialSymbols.Save,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(end = 5.dp),
        )
        Text(stringRes(R.string.device_transfer_export_button), color = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
private fun ImportSection(accountViewModel: AccountViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var chosenFile by remember { mutableStateOf<Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<AccountTransferBundle?>(null) }
    var busy by remember { mutableStateOf(false) }
    // Off by default, and deliberately a separate decision: restoring consent
    // records lets the file decide which apps may sign with the user's key.
    var includePermissions by remember { mutableStateOf(false) }

    val openLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            chosenFile = uri
            preview = null
            errorMessage = ""
        }

    SectionTitle(stringRes(R.string.device_transfer_import_title))

    Text(
        // An account that is currently loaded keeps its settings in memory and
        // would write them back over the imported ones on its next save, so the
        // import only fully lands after a restart.
        text = stringRes(R.string.device_transfer_import_explainer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.grayText,
    )

    Spacer(Modifier.height(12.dp))

    Button(
        // Not narrowed to the transfer MIME type: a file that has been through a
        // cloud drive or a chat app often comes back as application/octet-stream
        // or text/plain, and a picker that greys out the user's own backup is
        // indistinguishable from the feature being broken.
        onClick = { openLauncher.launch(arrayOf("*/*")) },
        enabled = !busy,
        shape = ButtonBorder,
        contentPadding = ButtonPadding,
    ) {
        Icon(
            symbol = MaterialSymbols.FileOpen,
            contentDescription = null,
            modifier = Modifier.padding(end = 5.dp),
        )
        Text(stringRes(R.string.device_transfer_choose_file))
    }

    val file = chosenFile ?: return

    Spacer(Modifier.height(12.dp))

    PasswordField(
        value = password,
        onValueChange = {
            password = it
            errorMessage = ""
        },
        placeholder = stringRes(R.string.device_transfer_password),
    )

    if (errorMessage.isNotBlank()) {
        Text(
            text = errorMessage,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    Spacer(Modifier.height(12.dp))

    val loaded = preview
    if (loaded == null) {
        // Unlocking is a separate step from importing so the user sees what the
        // file holds — and learns the password is right — before anything on
        // this device changes.
        Button(
            onClick = {
                busy = true
                scope.launch {
                    try {
                        preview = AccountTransferService.preview(readBytes(context, file), password)
                    } catch (e: AccountTransferEnvelope.InvalidTransferFile) {
                        errorMessage = e.message ?: stringRes(context, R.string.device_transfer_import_failed)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w("DeviceTransfer", "Could not read the transfer file", e)
                        errorMessage = stringRes(context, R.string.device_transfer_import_failed)
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy && password.isNotEmpty(),
            shape = ButtonBorder,
            contentPadding = ButtonPadding,
        ) {
            Icon(symbol = MaterialSymbols.LockOpen, contentDescription = null, modifier = Modifier.padding(end = 5.dp))
            Text(stringRes(R.string.device_transfer_unlock))
        }
    } else {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringRes(R.string.device_transfer_preview, AccountTransferService.importableAccounts(loaded).size.toString()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                loaded.appVersion?.let {
                    Text(
                        text = stringRes(R.string.device_transfer_preview_version, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.grayText,
                    )
                }

                // Accounts that sign through an external app carry no key. Say so
                // before the import, not after: the user needs to know they still
                // have that app to hand.
                val needReconnect = remember(loaded) { AccountTransferService.accountsNeedingReconnect(loaded) }
                if (needReconnect.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringRes(R.string.device_transfer_needs_reconnect, needReconnect.size.toString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = includePermissions, onCheckedChange = { includePermissions = it })
            Column(Modifier.padding(start = 12.dp)) {
                Text(stringRes(R.string.device_transfer_restore_permissions), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = stringRes(R.string.device_transfer_restore_permissions_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                busy = true
                scope.launch {
                    try {
                        AccountTransferService.import(loaded, includePermissions)
                        password = ""
                        chosenFile = null
                        preview = null
                        accountViewModel.toastManager.toast(R.string.device_transfer, R.string.device_transfer_import_done)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w("DeviceTransfer", "Could not apply the transfer file", e)
                        accountViewModel.toastManager.toast(R.string.device_transfer, R.string.device_transfer_import_failed, e)
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy,
            shape = ButtonBorder,
            contentPadding = ButtonPadding,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Icon(
                symbol = MaterialSymbols.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(end = 5.dp),
            )
            Text(stringRes(R.string.device_transfer_import_button), color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    var visible by remember { mutableStateOf(false) }

    OutlinedTextField(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.Password },
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
        placeholder = { Text(text = placeholder, color = MaterialTheme.colorScheme.placeholderText) },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    symbol = if (visible) MaterialSymbols.VisibilityOff else MaterialSymbols.Visibility,
                    contentDescription =
                        if (visible) stringRes(R.string.hide_password) else stringRes(R.string.show_password),
                )
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
    )
}

private const val TRANSFER_MIME_TYPE = "application/octet-stream"

private fun defaultFileName() = "amethyst-transfer.amethystbackup"

private suspend fun readBytes(
    context: Context,
    uri: Uri,
): ByteArray =
    withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Could not open the chosen file for reading")
    }

@Preview(device = "spec:width=2160px,height=2340px,dpi=440")
@Composable
fun DeviceTransferScreenPreview() {
    ThemeComparisonRow {
        DeviceTransferScreen(mockAccountViewModel(), EmptyNav())
    }
}

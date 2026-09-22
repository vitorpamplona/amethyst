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

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.marmot_invite_device_checking
import com.vitorpamplona.amethyst.commons.resources.marmot_invite_device_error
import com.vitorpamplona.amethyst.commons.resources.marmot_invite_device_none
import com.vitorpamplona.amethyst.commons.resources.marmot_invite_device_other
import com.vitorpamplona.amethyst.commons.resources.marmot_invite_device_publish
import com.vitorpamplona.amethyst.commons.resources.marmot_invite_device_this
import com.vitorpamplona.amethyst.commons.resources.marmot_invite_device_title
import com.vitorpamplona.amethyst.commons.resources.reset_marmot_confirm_action
import com.vitorpamplona.amethyst.commons.resources.reset_marmot_confirm_body
import com.vitorpamplona.amethyst.commons.resources.reset_marmot_confirm_title
import com.vitorpamplona.amethyst.commons.resources.settings_search_no_results
import com.vitorpamplona.amethyst.model.LatestKeyPackageOwner
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.SettingsSearchTopBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

@Preview
@Composable
fun AllSettingsScreenPreview() {
    ThemeComparisonColumn {
        AllSettingsScreen(
            mockAccountViewModel(),
            EmptyNav(),
        )
    }
}

@Composable
fun AllSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var showResetMarmotDialog by remember { mutableStateOf(false) }
    var isResettingMarmot by remember { mutableStateOf(false) }
    var showInviteDeviceDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val hasPrivateKey = accountViewModel.account.settings.keyPair.privKey != null

    var query by rememberSaveable { mutableStateOf("") }

    // The catalog is structurally stable for the screen's lifetime, so it is rebuilt only when
    // an input actually changes — not on every keystroke. `onResetMarmot` reads the volatile
    // `isResettingMarmot` through `rememberUpdatedState` so the memoized closure never goes stale.
    val onResetMarmot by rememberUpdatedState(newValue = { if (!isResettingMarmot) showResetMarmotDialog = true })
    val onMarmotInviteDevice by rememberUpdatedState(newValue = { showInviteDeviceDialog = true })
    val catalog =
        remember(hasPrivateKey, nav, uriHandler) {
            buildSettingsCatalog(
                nav = nav,
                uriHandler = uriHandler,
                hasPrivateKey = hasPrivateKey,
                onResetMarmot = { onResetMarmot() },
                onMarmotInviteDevice = { onMarmotInviteDevice() },
            )
        }

    val filtered =
        filterSettings(
            catalog = catalog,
            query = query,
            stringLookup = { stringRes(context, it) },
        )

    Scaffold(
        topBar = {
            SettingsSearchTopBar(
                query = query,
                onQueryChange = { query = it },
                onClear = { query = "" },
                nav = nav,
            )
        },
        bottomBar = {
            AppBottomBar(Route.AllSettings, nav, accountViewModel) { route ->
                if (route == Route.AllSettings) {
                    scope.launch { scrollState.animateScrollTo(0) }
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Filtering happens in place: the list below is the search result. A blank query
            // yields the whole catalog, narrowing as the user types via the top-bar search field.
            if (filtered.isEmpty()) {
                SettingsSearchEmptyState(
                    query = query,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    modifier =
                        Modifier
                            .verticalScroll(scrollState)
                            .padding(top = 12.dp)
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    filtered.forEach { category -> SettingsCategoryCard(category) }
                }
            }
        }
    }

    if (showInviteDeviceDialog) {
        MarmotInviteDeviceDialog(
            accountViewModel = accountViewModel,
            // Published from the screen's scope, not the dialog's: confirming
            // closes the dialog, and a publish launched in the dialog's own
            // scope would be cancelled the moment it left composition.
            onConfirm = {
                showInviteDeviceDialog = false
                scope.launch(Dispatchers.IO) {
                    val successMessage = stringRes(context, R.string.marmot_invite_device_success)
                    val rejectedMessage = stringRes(context, R.string.marmot_invite_device_rejected)
                    try {
                        // Waits for a relay OK. The fire-and-forget publish this
                        // replaced reported success for a read-only account, an
                        // empty relay set and an outright rejection alike.
                        val accepted = accountViewModel.republishKeyPackage()
                        launch(Dispatchers.Main) {
                            val message = if (accepted) successMessage else rejectedMessage
                            Toast.makeText(context, message, if (accepted) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        val failureMessage =
                            stringRes(context, R.string.marmot_invite_device_failure, e.message ?: "")
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, failureMessage, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
            onDismiss = { showInviteDeviceDialog = false },
        )
    }

    if (showResetMarmotDialog) {
        ResetMarmotStateDialog(
            onConfirm = {
                showResetMarmotDialog = false
                isResettingMarmot = true
                scope.launch(Dispatchers.IO) {
                    val successMessage = stringRes(context, R.string.reset_marmot_success)
                    try {
                        accountViewModel.resetMarmotState()
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        val failureMessage =
                            stringRes(context, R.string.reset_marmot_failure, e.message ?: "")
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, failureMessage, Toast.LENGTH_LONG).show()
                        }
                    } finally {
                        isResettingMarmot = false
                    }
                }
            },
            onDismiss = { showResetMarmotDialog = false },
        )
    }
}

@Composable
private fun SettingsSearchEmptyState(
    query: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = stringRes(Res.string.settings_search_no_results, query),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp),
        )
    }
}

@Composable
private fun SettingsCategoryCard(category: SettingsCategory) {
    SettingsSection(category.titleRes, category.isDanger) {
        category.entries.forEachIndexed { index, entry ->
            if (index > 0) SettingsDivider()
            SettingsEntryRow(entry)
        }
    }
}

@Composable
private fun SettingsEntryRow(entry: SettingsEntry) {
    when (val icon = entry.icon) {
        is SettingsIcon.Symbol ->
            SettingsItem(
                title = entry.titleRes,
                icon = icon.symbol,
                isDanger = entry.isDanger,
                onClick = entry.onClick,
            )
        is SettingsIcon.Painter ->
            SettingsItem(
                title = entry.titleRes,
                iconPainter = icon.iconPainter,
                iconPainterRef = icon.iconPainterRef,
                isDanger = entry.isDanger,
                onClick = entry.onClick,
            )
    }
}

@Composable
private fun ResetMarmotStateDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                symbol = MaterialSymbols.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                text = stringRes(Res.string.reset_marmot_confirm_title),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(text = stringRes(Res.string.reset_marmot_confirm_body))
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text(stringRes(Res.string.reset_marmot_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.cancel))
            }
        },
    )
}

/**
 * Reports which install currently receives this account's Marmot invites, and
 * offers to move them to this one.
 *
 * The check is this dialog's *content*, never a trigger. An inviter always
 * takes the newest kind:30443 and nothing else, so a device that silently
 * republished itself back to the front whenever it lost would deadlock against
 * the other install — each device's correction is the other's trigger, and
 * neither ever settles. Surfacing the answer and letting the user decide is
 * what keeps two signed-in devices from fighting over the account's invites.
 */
@Composable
private fun MarmotInviteDeviceDialog(
    accountViewModel: AccountViewModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var owner by remember { mutableStateOf<LatestKeyPackageOwner?>(null) }
    var checkFailed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            owner = withContext(Dispatchers.IO) { accountViewModel.latestKeyPackageOwner() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A relay we cannot reach only costs us the diagnosis, not the
            // action: publishing from here is still a valid thing to want.
            checkFailed = true
        }
    }

    val resolved = owner
    val body =
        when {
            checkFailed -> stringRes(Res.string.marmot_invite_device_error)
            resolved == null -> stringRes(Res.string.marmot_invite_device_checking)
            resolved == LatestKeyPackageOwner.THIS_DEVICE -> stringRes(Res.string.marmot_invite_device_this)
            resolved == LatestKeyPackageOwner.OTHER_DEVICE -> stringRes(Res.string.marmot_invite_device_other)
            else -> stringRes(Res.string.marmot_invite_device_none)
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                symbol = MaterialSymbols.Key,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                text = stringRes(Res.string.marmot_invite_device_title),
                textAlign = TextAlign.Center,
            )
        },
        text = { Text(text = body) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                // Held until the check resolves so the user is never asked to
                // act on an answer that has not arrived.
                enabled = checkFailed || resolved != null,
            ) {
                Text(stringRes(Res.string.marmot_invite_device_publish))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.cancel))
            }
        },
    )
}

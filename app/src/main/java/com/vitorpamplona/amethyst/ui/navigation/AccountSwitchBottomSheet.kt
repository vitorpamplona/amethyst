/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.AccountInfo
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedOff.LoginOrSignupScreen
import com.vitorpamplona.amethyst.ui.theme.AccountPictureModifier
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size55dp
import com.vitorpamplona.quartz.encoders.decodePublicKeyAsHexOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSwitchBottomSheet(
    accountViewModel: AccountViewModel,
    accountStateViewModel: AccountStateViewModel,
) {
    val accounts = LocalPreferences.allSavedAccounts()

    var popupExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.verticalScroll(scrollState)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Size10dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.account_switch_select_account), fontWeight = FontWeight.Bold)
        }
        accounts.forEach { acc -> DisplayAccount(acc, accountViewModel, accountStateViewModel) }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = Size10dp, bottom = Size55dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { popupExpanded = true }) {
                Text(stringResource(R.string.account_switch_add_account_btn))
            }
        }
    }

    if (popupExpanded) {
        Dialog(
            onDismissRequest = { popupExpanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box {
                    LoginOrSignupScreen(accountStateViewModel, isFirstLogin = false)
                    TopAppBar(
                        title = {
                            Text(text = stringResource(R.string.account_switch_add_account_dialog_title))
                        },
                        navigationIcon = {
                            IconButton(onClick = { popupExpanded = false }) { ArrowBackIcon() }
                        },
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
fun DisplayAccount(
    acc: AccountInfo,
    accountViewModel: AccountViewModel,
    accountStateViewModel: AccountStateViewModel,
) {
    var baseUser by remember {
        mutableStateOf<User?>(
            decodePublicKeyAsHexOrNull(acc.npub)?.let {
                LocalCache.getUserIfExists(it)
            },
        )
    }

    if (baseUser == null) {
        LaunchedEffect(key1 = acc.npub) {
            launch(Dispatchers.IO) {
                baseUser =
                    decodePublicKeyAsHexOrNull(acc.npub)?.let {
                        LocalCache.getOrCreateUser(it)
                    }
            }
        }
    }

    baseUser?.let {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { accountStateViewModel.switchUser(acc) }
                    .padding(16.dp, 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .width(55.dp)
                                .padding(0.dp),
                    ) {
                        val automaticallyShowProfilePicture =
                            remember {
                                accountViewModel.settings.showProfilePictures.value
                            }

                        AccountPicture(it, automaticallyShowProfilePicture)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) { AccountName(acc, it) }
                    Column(modifier = Modifier.width(32.dp)) { ActiveMarker(acc, accountViewModel) }
                }
            }

            LogoutButton(acc, accountStateViewModel)
        }
    }
}

@Composable
private fun ActiveMarker(
    acc: AccountInfo,
    accountViewModel: AccountViewModel,
) {
    val isCurrentUser by
        remember(accountViewModel) {
            derivedStateOf { accountViewModel.account.userProfile().pubkeyNpub() == acc.npub }
        }

    if (isCurrentUser) {
        Icon(
            imageVector = Icons.Default.RadioButtonChecked,
            contentDescription = stringResource(R.string.account_switch_active_account),
            tint = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun AccountPicture(
    user: User,
    loadProfilePicture: Boolean,
) {
    val profilePicture by user.live().profilePictureChanges.observeAsState()

    RobohashFallbackAsyncImage(
        robot = user.pubkeyHex,
        model = profilePicture,
        contentDescription = stringResource(R.string.profile_image),
        modifier = AccountPictureModifier,
        loadProfilePicture = loadProfilePicture,
    )
}

@Composable
private fun AccountName(
    acc: AccountInfo,
    user: User,
) {
    val info by user.live().userMetadataInfo.observeAsState()

    info?.let {
        it.bestName()?.let { name ->
            CreateTextWithEmoji(
                text = name,
                tags = it.tags,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    Text(
        text = remember(user) { acc.npub.toShortenHex() },
    )
}

@Composable
private fun LogoutButton(
    acc: AccountInfo,
    accountStateViewModel: AccountStateViewModel,
) {
    var logoutDialog by remember { mutableStateOf(false) }
    if (logoutDialog) {
        AlertDialog(
            title = { Text(text = stringResource(R.string.log_out)) },
            text = { Text(text = stringResource(R.string.are_you_sure_you_want_to_log_out)) },
            onDismissRequest = { logoutDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        logoutDialog = false
                        accountStateViewModel.logOff(acc)
                    },
                ) {
                    Text(text = stringResource(R.string.log_out))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { logoutDialog = false },
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }

    IconButton(
        onClick = { logoutDialog = true },
    ) {
        Icon(
            imageVector = Icons.Default.Logout,
            contentDescription = stringResource(R.string.log_out),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

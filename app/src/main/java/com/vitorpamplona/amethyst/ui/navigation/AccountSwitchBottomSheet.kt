package com.vitorpamplona.amethyst.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.components.ResizeImage
import com.vitorpamplona.amethyst.ui.components.RobohashAsyncImageProxy
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedOff.LoginPage
import nostr.postr.bechToBytes
import nostr.postr.toHex

@Composable
fun AccountSwitchBottomSheet(
    accountViewModel: AccountViewModel,
    accountStateViewModel: AccountStateViewModel
) {
    val context = LocalContext.current
    val accounts = LocalPreferences.allSavedAccounts()

    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    val accountUserState by account.userProfile().live().metadata.observeAsState()
    val accountUser = accountUserState?.user ?: return

    var popupExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.verticalScroll(scrollState)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.account_switch_select_account), fontWeight = FontWeight.Bold)
        }
        accounts.forEach { acc ->
            val current = accountUser.pubkeyNpub() == acc.npub

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            accountStateViewModel.switchUser(acc.npub)
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp, 16.dp)
                            .weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(55.dp)
                                .padding(0.dp)
                        ) {
                            RobohashAsyncImageProxy(
                                robot = acc.npub.bechToBytes("npub").toHex(),
                                model = ResizeImage(acc.profilePicture, 55.dp),
                                contentDescription = stringResource(R.string.profile_image),
                                modifier = Modifier
                                    .width(55.dp)
                                    .height(55.dp)
                                    .clip(shape = CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.TopEnd)
                            ) {
                                if (acc.hasPrivKey) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = stringResource(R.string.account_switch_has_private_key),
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colors.primary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = stringResource(R.string.account_switch_pubkey_only),
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colors.primary
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val npubShortHex = acc.npub.toShortenHex()

                            if (acc.displayName != null && acc.displayName != npubShortHex) {
                                Text(acc.displayName)
                            }

                            Text(npubShortHex)
                        }
                        Column(modifier = Modifier.width(32.dp)) {
                            if (current) {
                                Icon(
                                    imageVector = Icons.Default.RadioButtonChecked,
                                    contentDescription = stringResource(R.string.account_switch_active_account),
                                    tint = MaterialTheme.colors.secondary
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = { accountStateViewModel.logOff(acc.npub) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = stringResource(R.string.log_out),
                        tint = MaterialTheme.colors.onSurface
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { popupExpanded = true }) {
                Text(stringResource(R.string.account_switch_add_account_btn))
            }
        }
    }

    if (popupExpanded) {
        Dialog(
            onDismissRequest = { popupExpanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box {
                    LoginPage(accountStateViewModel, isFirstLogin = false)
                    TopAppBar(
                        title = { Text(text = stringResource(R.string.account_switch_add_account_dialog_title)) },
                        navigationIcon = {
                            IconButton(onClick = { popupExpanded = false }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = stringResource(R.string.back),
                                    tint = MaterialTheme.colors.onSurface
                                )
                            }
                        },
                        backgroundColor = Color.Transparent,
                        elevation = 0.dp
                    )
                }
            }
        }
    }
}

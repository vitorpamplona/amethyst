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
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.RadioButtonChecked
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.vitorpamplona.amethyst.model.decodePublicKey
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.ui.actions.toImmutableListOfLists
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.ResizeImage
import com.vitorpamplona.amethyst.ui.components.RobohashAsyncImageProxy
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedOff.LoginPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun AccountSwitchBottomSheet(
    accountViewModel: AccountViewModel,
    accountStateViewModel: AccountStateViewModel
) {
    val accounts = LocalPreferences.allSavedAccounts()

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
            DisplayAccount(acc, accountViewModel, accountStateViewModel)
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

@Composable
fun DisplayAccount(
    acc: AccountInfo,
    accountViewModel: AccountViewModel,
    accountStateViewModel: AccountStateViewModel
) {
    var baseUser by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(key1 = acc.npub) {
        launch(Dispatchers.IO) {
            baseUser = try {
                LocalCache.getOrCreateUser(decodePublicKey(acc.npub).toHexKey())
            } catch (e: Exception) {
                null
            }
        }
    }

    baseUser?.let {
        Row(
            modifier = Modifier.fillMaxWidth().clickable {
                accountStateViewModel.switchUser(acc.npub)
            }.padding(16.dp, 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.width(55.dp).padding(0.dp)
                    ) {
                        AccountPicture(it)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        AccountName(acc, it)
                    }
                    Column(modifier = Modifier.width(32.dp)) {
                        ActiveMarker(acc, accountViewModel)
                    }
                }
            }

            LogoutButton(acc, accountStateViewModel)
        }
    }
}

@Composable
private fun ActiveMarker(acc: AccountInfo, accountViewModel: AccountViewModel) {
    val isCurrentUser by remember(accountViewModel) {
        derivedStateOf {
            accountViewModel.account.userProfile().pubkeyNpub() == acc.npub
        }
    }

    if (isCurrentUser) {
        Icon(
            imageVector = Icons.Default.RadioButtonChecked,
            contentDescription = stringResource(R.string.account_switch_active_account),
            tint = MaterialTheme.colors.secondary
        )
    }
}

@Composable
private fun AccountPicture(user: User) {
    val userState by user.live().metadata.observeAsState()
    val profilePicture by remember(userState) {
        derivedStateOf {
            ResizeImage(userState?.user?.profilePicture(), 55.dp)
        }
    }

    RobohashAsyncImageProxy(
        robot = remember(user) { user.pubkeyHex },
        model = profilePicture,
        contentDescription = stringResource(R.string.profile_image),
        modifier = Modifier
            .width(55.dp)
            .height(55.dp)
            .clip(shape = CircleShape)
    )
}

@Composable
private fun AccountName(
    acc: AccountInfo,
    user: User
) {
    val userState by user.live().metadata.observeAsState()
    val displayName by remember(userState) {
        derivedStateOf {
            user.bestDisplayName()
        }
    }
    val tags by remember(userState) {
        derivedStateOf {
            user.info?.latestMetadata?.tags?.toImmutableListOfLists()
        }
    }

    displayName?.let {
        CreateTextWithEmoji(
            text = it,
            tags = tags,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    Text(
        text = remember(user) { acc.npub.toShortenHex() }
    )
}

@Composable
private fun LogoutButton(
    acc: AccountInfo,
    accountStateViewModel: AccountStateViewModel
) {
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

package com.vitorpamplona.amethyst.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.RoboHashCache
import com.vitorpamplona.amethyst.ui.components.AsyncImageProxy
import com.vitorpamplona.amethyst.ui.components.ResizeImage
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AccountSwitchBottomSheet(
    accountViewModel: AccountViewModel,
    sheetState: ModalBottomSheetState
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val localPrefs = LocalPreferences(context)
    val accounts = localPrefs.findAllLocalAccounts()

    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    val accountUserState by account.userProfile().live().metadata.observeAsState()
    val accountUser = accountUserState?.user ?: return

    LaunchedEffect(key1 = accountUser) {
        localPrefs.saveCurrentAccountMetadata(account)
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Select Account", fontWeight = FontWeight.Bold)
        }
        accounts.forEach { acc ->
            val current = accountUser.pubkeyNpub() == acc.npub

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp, 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImageProxy(
                    model = ResizeImage(acc.profilePicture, 64.dp),
                    placeholder = BitmapPainter(RoboHashCache.get(context, acc.npub)),
                    fallback = BitmapPainter(RoboHashCache.get(context, acc.npub)),
                    error = BitmapPainter(RoboHashCache.get(context, acc.npub)),
                    contentDescription = stringResource(id = R.string.profile_image),
                    modifier = Modifier
                        .width(64.dp)
                        .height(64.dp)
                        .clip(shape = CircleShape)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    acc.displayName?.let {
                        Text(it)
                    }
                    Text(acc.npub.toShortenHex())
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (current) {
                    Text("✓")
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { /*TODO*/ }) {
                    Icon(imageVector = Icons.Default.Logout, "Logout")
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
            TextButton(onClick = { coroutineScope.launch { sheetState.hide() } }) {
                Text("Add New Account")
            }
        }
    }
}

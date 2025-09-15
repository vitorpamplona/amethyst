/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.DisplayNIP05
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.components.SetDialogToEdgeToEdge
import com.vitorpamplona.amethyst.ui.components.nip05VerificationAsAState
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.largeProfilePictureModifier
import com.vitorpamplona.quartz.nip01Core.metadata.UserMetadata

@Preview
@Composable
fun ShowQRDialogPreview() {
    val accountViewModel = mockAccountViewModel()
    accountViewModel.userProfile().info =
        UserMetadata().apply {
            name = "My Name"
            picture = "Picture"
            nip05 = null
            banner = "http://banner.com/test"
            website = "http://mywebsite.com/test"
            about = "This is the about me"
        }

    ShowQRDialog(
        user = accountViewModel.userProfile(),
        accountViewModel = accountViewModel,
        onScan = {},
        onClose = {},
    )
}

@Composable
fun BackButton(onPress: () -> Unit) {
    IconButton(
        onClick = onPress,
    ) {
        ArrowBackIcon(MaterialTheme.colorScheme.onBackground)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowQRDialog(
    user: User,
    accountViewModel: AccountViewModel,
    onScan: (Route) -> Unit,
    onClose: () -> Unit,
) {
    var presenting by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        SetDialogToEdgeToEdge()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        Row {
                            Spacer(modifier = StdHorzSpacer)
                            BackButton(onPress = onClose)
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                )
            },
        ) { pad ->
            Surface(
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = 10.dp,
                        end = 10.dp,
                        top = pad.calculateTopPadding(),
                        bottom = pad.calculateBottomPadding(),
                    ).consumeWindowInsets(pad)
                    .imePadding(),
            ) {
                Column {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                        verticalArrangement = Arrangement.SpaceAround,
                    ) {
                        if (presenting) {
                            Column {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    RobohashFallbackAsyncImage(
                                        robot = user.pubkeyHex,
                                        model = user.profilePicture(),
                                        contentDescription = stringRes(R.string.profile_image),
                                        modifier = MaterialTheme.colorScheme.largeProfilePictureModifier,
                                        loadProfilePicture = accountViewModel.settings.showProfilePictures(),
                                        loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                ) {
                                    CreateTextWithEmoji(
                                        text = user.info?.bestName() ?: "",
                                        tags = user.info?.tags,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                ) {
                                    val nip05 = user.nip05()
                                    if (nip05 != null) {
                                        val nip05Verified =
                                            nip05VerificationAsAState(user.info!!, user.pubkeyHex, accountViewModel)

                                        DisplayNIP05(nip05, nip05Verified, accountViewModel)
                                    } else {
                                        Text(
                                            text = user.pubkeyDisplayHex(),
                                            fontSize = Font14SP,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = Size10dp),
                            ) {
                                QrCodeDrawer(user.toNostrUri())
                            }

                            Row(modifier = Modifier.padding(horizontal = 30.dp)) {
                                FilledTonalButton(
                                    onClick = { presenting = false },
                                    shape = RoundedCornerShape(Size35dp),
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                ) {
                                    Text(text = stringRes(R.string.scan_qr))
                                }
                            }
                        } else {
                            NIP19QrCodeScanner(accountViewModel) {
                                if (it == null) {
                                    presenting = true
                                } else {
                                    onScan(it)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

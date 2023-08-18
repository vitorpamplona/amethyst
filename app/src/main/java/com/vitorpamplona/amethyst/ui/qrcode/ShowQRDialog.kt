package com.vitorpamplona.amethyst.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.actions.CloseButton
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.RobohashAsyncImageProxy
import com.vitorpamplona.amethyst.ui.qrcode.NIP19QrCodeScanner
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.quartz.events.toImmutableListOfLists

@Composable
fun ShowQRDialog(user: User, onScan: (String) -> Unit, onClose: () -> Unit) {
    var presenting by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colors.background)
                    .fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CloseButton(onCancel = onClose)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.SpaceAround
                ) {
                    if (presenting) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                                RobohashAsyncImageProxy(
                                    robot = user.pubkeyHex,
                                    model = user.profilePicture(),
                                    contentDescription = stringResource(R.string.profile_image),
                                    modifier = Modifier
                                        .width(100.dp)
                                        .height(100.dp)
                                        .clip(shape = CircleShape)
                                        .border(3.dp, MaterialTheme.colors.background, CircleShape)
                                        .background(MaterialTheme.colors.background)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(top = 5.dp)) {
                                CreateTextWithEmoji(
                                    text = user.bestDisplayName() ?: user.bestUsername() ?: "",
                                    tags = user.info?.latestMetadata?.tags?.toImmutableListOfLists(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Size35dp)
                        ) {
                            QrCodeDrawer("nostr:${user.pubkeyNpub()}")
                        }

                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 30.dp)
                        ) {
                            Button(
                                onClick = { presenting = false },
                                shape = RoundedCornerShape(Size35dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                colors = ButtonDefaults
                                    .buttonColors(
                                        backgroundColor = MaterialTheme.colors.primary
                                    )
                            ) {
                                Text(text = stringResource(R.string.scan_qr))
                            }
                        }
                    } else {
                        NIP19QrCodeScanner {
                            if (it.isNullOrEmpty()) {
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

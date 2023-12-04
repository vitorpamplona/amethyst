package com.vitorpamplona.amethyst.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.startActivity
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.CashuProcessor
import com.vitorpamplona.amethyst.service.CashuToken
import com.vitorpamplona.amethyst.ui.actions.LoadingAnimation
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun CashuPreview(cashutoken: String, accountViewModel: AccountViewModel) {
    var cachuData by remember {
        mutableStateOf<GenericLoadable<CashuToken>>(GenericLoadable.Loading())
    }

    LaunchedEffect(key1 = cashutoken) {
        launch(Dispatchers.IO) {
            val newCachuData = CashuProcessor().parse(cashutoken)
            launch(Dispatchers.Main) {
                cachuData = newCachuData
            }
        }
    }

    Crossfade(targetState = cachuData, label = "CashuPreview(") {
        when (it) {
            is GenericLoadable.Loaded<CashuToken> -> CashuPreview(it.loaded, accountViewModel)
            is GenericLoadable.Error<CashuToken> -> Text(
                text = "$cashutoken ",
                style = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
            )
            else -> {}
        }
    }
}

@Composable
fun CashuPreview(token: CashuToken, accountViewModel: AccountViewModel) {
    val lud16 = remember(accountViewModel) {
        accountViewModel.account.userProfile().info?.lud16
    }

    val useWebService = false
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 10.dp)
            .clip(shape = QuoteBorder)
            .border(1.dp, MaterialTheme.colorScheme.subtleBorder, QuoteBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.cashu),
                    null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.Unspecified
                )

                Text(
                    text = stringResource(R.string.cashu),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W500,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }

            Divider()

            Text(
                text = "${token.totalAmount} ${stringResource(id = R.string.sats)}",
                fontSize = 25.sp,
                fontWeight = FontWeight.W500,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            )

            Row(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .fillMaxWidth()
            ) {
                var isRedeeming by remember {
                    mutableStateOf(false)
                }

                Button(
                    onClick = {
                        if (lud16 != null) {
                            scope.launch(Dispatchers.IO) {
                                isRedeeming = true
                                CashuProcessor().melt(
                                    token,
                                    lud16,
                                    onSuccess = { title, message ->
                                        isRedeeming = false
                                        accountViewModel.toast(title, message)
                                    },
                                    onError = { title, message ->
                                        isRedeeming = false
                                        accountViewModel.toast(title, message)
                                    },
                                    context
                                )
                            }
                        } else {
                            accountViewModel.toast(
                                context.getString(R.string.no_lightning_address_set),
                                context.getString(R.string.user_x_does_not_have_a_lightning_address_setup_to_receive_sats, accountViewModel.account.userProfile().toBestDisplayName())
                            )
                        }
                    },
                    shape = QuoteBorder,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isRedeeming) {
                        LoadingAnimation()
                    }

                    Text(
                        "⚡ Send to Zap Wallet",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = StdHorzSpacer)
            Button(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("cashu://$token"))
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

                        startActivity(context, intent, null)
                    } catch (e: Exception) {
                        accountViewModel.toast("Cashu", context.getString(R.string.cashu_no_wallet_found))
                    }
                },
                shape = QuoteBorder,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("\uD83E\uDD5C Open in Cashu Wallet", color = Color.White, fontSize = 16.sp)
            }
            Spacer(modifier = StdHorzSpacer)
            Button(
                onClick = {
                    // Copying the token to clipboard
                    clipboardManager.setText(AnnotatedString(token.token))
                },
                shape = QuoteBorder,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("⎘ Copy ", color = Color.White, fontSize = 16.sp)
            }
            Spacer(modifier = StdHorzSpacer)
        }
    }
}

package com.vitorpamplona.amethyst.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun CashuPreview(cashutoken: String, accountViewModel: AccountViewModel) {
    var cachuData by remember { mutableStateOf<GenericLoadable<CashuToken>>(GenericLoadable.Loading<CashuToken>()) }

    LaunchedEffect(key1 = cashutoken) {
        launch(Dispatchers.IO) {
            val newCachuData = CashuProcessor().parse(cashutoken)
            launch(Dispatchers.Main) {
                cachuData = newCachuData
            }
        }
    }

    Crossfade(targetState = cachuData) {
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
            .padding(start = 30.dp, end = 30.dp)
            .clip(shape = QuoteBorder)
            .border(1.dp, MaterialTheme.colorScheme.subtleBorder, QuoteBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(30.dp)
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

            token.totalAmount.let {
                Text(
                    text = "$it ${stringResource(id = R.string.sats)}",
                    fontSize = 25.sp,
                    fontWeight = FontWeight.W500,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                )
            }

            Row(

                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Button(

                    modifier = Modifier
                        .padding(vertical = 10.dp).padding(horizontal = 2.dp),
                    onClick = {
                        if (lud16 != null) {
                            scope.launch(Dispatchers.IO) {
                                CashuProcessor().melt(
                                    token,
                                    lud16,
                                    onSuccess = { title, message ->
                                        accountViewModel.toast(title, message)
                                    },
                                    onError = { title, message ->
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
                    Text(
                        stringResource(R.string.cashu_redeem),
                        color = Color.White,
                        fontSize = 18.sp
                    )
                }
                Button(
                    modifier = Modifier
                        .padding(vertical = 10.dp).padding(horizontal = 1.dp),
                    onClick = {
                        if (useWebService) {
                            // In case we want to use the cashu.me webservice
                            val url = "https://redeem.cashu.me?token=$token&lightning=$lud16&autopay=false"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            startActivity(context, intent, null)
                        } else {
                            // Copying the token to clipboard for now
                            clipboardManager.setText(AnnotatedString(token.token))
                        }
                    },
                    shape = QuoteBorder,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("⎘", color = Color.White, fontSize = 18.sp)
                }
            }
        }
    }
}

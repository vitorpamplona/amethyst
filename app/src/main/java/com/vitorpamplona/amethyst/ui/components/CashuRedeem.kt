package com.vitorpamplona.amethyst.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
    var cachuData by remember { mutableStateOf<CashuToken?>(null) }

    LaunchedEffect(key1 = cashutoken) {
        launch(Dispatchers.IO) {
            cachuData = CashuProcessor().parse(cashutoken)
        }
    }

    Crossfade(targetState = cachuData) {
        if (it != null) {
            CashuPreview(it, accountViewModel)
        } else {
            Text(
                text = "$cashutoken ",
                style = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
            )
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 30.dp, end = 30.dp)
            .clip(shape = QuoteBorder)
            .border(1.dp, MaterialTheme.colors.subtleBorder, QuoteBorder)
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

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                onClick = {
                    // Just in case we want to use a webservice instead of directly contacting the mint
                    if (useWebService) {
                        val url = "https://redeem.cashu.me?token=$token&lightning=$lud16&autopay=true"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(context, intent, null)
                    } else {
                        if (lud16 != null) {
                            CashuProcessor().melt(
                                token,
                                lud16,
                                onSuccess = {
                                    scope.launch {
                                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onError = {
                                    scope.launch {
                                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        } else {
                            scope.launch {
                                Toast.makeText(context, "No Lightning Address set", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                shape = QuoteBorder,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary
                )
            ) {
                Text(stringResource(R.string.cashu_redeem), color = Color.White, fontSize = 20.sp)
            }
        }
    }
}

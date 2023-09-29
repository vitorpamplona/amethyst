package com.vitorpamplona.amethyst.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDirection
import androidx.core.content.ContextCompat
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.quartz.encoders.LnWithdrawalUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun MayBeWithdrawal(lnurlWord: String) {
    var lnWithdrawal by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(key1 = lnurlWord) {
        launch(Dispatchers.IO) {
            lnWithdrawal = LnWithdrawalUtil.findWithdrawal(lnurlWord)
        }
    }

    Crossfade(targetState = lnWithdrawal) {
        if (it != null) {
            ClickableWithdrawal(withdrawalString = it)
        } else {
            Text(
                text = lnurlWord,
                style = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
            )
        }
    }
}

@Composable
fun ClickableWithdrawal(withdrawalString: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val withdraw = remember(withdrawalString) {
        AnnotatedString("$withdrawalString ")
    }

    ClickableText(
        text = withdraw,
        onClick = {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("lightning:$withdrawalString"))
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                ContextCompat.startActivity(context, intent, null)
            } catch (e: Exception) {
                scope.launch {
                    Toast.makeText(
                        context,
                        context.getString(R.string.lightning_wallets_not_found),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        },
        style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.primary)
    )
}

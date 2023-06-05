package com.vitorpamplona.amethyst.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDirection
import androidx.core.content.ContextCompat
import com.vitorpamplona.amethyst.service.lnurl.LnWithdrawalUtil
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

    lnWithdrawal?.let {
        ClickableWithdrawal(withdrawalString = it)
    }
        ?: Text(
            text = "$lnurlWord ",
            style = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
        )
}

@Composable
fun ClickableWithdrawal(withdrawalString: String) {
    val context = LocalContext.current

    val uri = remember(withdrawalString) {
        Uri.parse("lightning:$withdrawalString")
    }

    val withdraw = remember(withdrawalString) {
        AnnotatedString("$withdrawalString ")
    }

    ClickableText(
        text = withdraw,
        onClick = {
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, uri)
                ContextCompat.startActivity(context, intent, null)
            }
        },
        style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary)
    )
}

package com.vitorpamplona.amethyst.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.core.content.ContextCompat

@Composable
fun ClickableWithdrawal(withdrawalString: String) {
    val context = LocalContext.current

    ClickableText(
        text = AnnotatedString("$withdrawalString "),
        onClick = {
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("lightning:$withdrawalString"))
                ContextCompat.startActivity(context, intent, null)
            }
        },
        style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary)
    )
}

package com.vitorpamplona.amethyst.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString

@Composable
fun ClickablePhone(phone: String) {
    val context = LocalContext.current

    ClickableText(
        text = AnnotatedString("$phone "),
        onClick = { runCatching { context.dial(phone) } },
        style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary)
    )
}

fun Context.dial(phone: String) {
    try {
        val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))
        startActivity(intent)
    } catch (t: Throwable) {
        // TODO: Handle potential exceptions
    }
}

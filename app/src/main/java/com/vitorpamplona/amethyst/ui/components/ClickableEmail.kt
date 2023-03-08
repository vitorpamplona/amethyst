package com.vitorpamplona.amethyst.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString

@Composable
fun ClickableEmail(email: String) {
    val context = LocalContext.current

    ClickableText(
        text = AnnotatedString("$email "),
        onClick = { runCatching { context.sendMail(email) } },
        style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary)
    )
}

fun Context.sendMail(to: String, subject: String? = null) {
    try {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "vnd.android.cursor.item/email" // or "message/rfc822"
        intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
        if (subject != null) {
            intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // TODO: Handle case where no email app is available
    } catch (t: Throwable) {
        // TODO: Handle potential other type of exceptions
    }
}

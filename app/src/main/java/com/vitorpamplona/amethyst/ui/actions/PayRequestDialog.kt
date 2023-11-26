package com.vitorpamplona.amethyst.ui.actions

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.components.InvoicePreview
import com.vitorpamplona.amethyst.ui.components.LoadValueFromInvoice
import com.vitorpamplona.amethyst.ui.theme.Size16dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer

@Composable
fun PayRequestDialog(
    title: String,
    textContent: String,
    lnInvoice: String?,
    textContent2: String,
    otherOptions: String?,
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(),
    onDismiss: () -> Unit
) {
    val uri = LocalUriHandler.current

    val uriOpener: @Composable (() -> Unit) = otherOptions?.let {
        {
            Button(
                onClick = {
                    runCatching {
                        uri.openUri(it)
                    }
                },
                colors = buttonColors,
                contentPadding = PaddingValues(horizontal = Size16dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.OpenInNew,
                        contentDescription = null
                    )
                    Spacer(StdHorzSpacer)
                    Text(stringResource(R.string.other_options))
                }
            }
        }
    } ?: {
        Row() {}
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title)
        },
        text = {
            Column {
                Text(textContent)
                Spacer(modifier = StdVertSpacer)
                if (lnInvoice != null) {
                    LoadValueFromInvoice(lnbcWord = lnInvoice) { invoiceAmount ->
                        Crossfade(targetState = invoiceAmount, label = "PayRequestDialog") {
                            if (it != null) {
                                InvoicePreview(it.invoice, it.amount)
                            } else {
                                Text(
                                    text = lnInvoice,
                                    style = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = StdVertSpacer)
                Text(textContent2)
            }
        },
        confirmButton = uriOpener,
        dismissButton = {
            TextButton(
                onClick = {
                    onDismiss()
                }
            ) {
                Text(text = stringResource(R.string.dismiss))
            }
        }
    )
}

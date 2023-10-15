package com.vitorpamplona.amethyst.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDirection
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.note.ErrorMessageDialog
import com.vitorpamplona.amethyst.ui.note.payViaIntent
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

    val withdraw = remember(withdrawalString) {
        AnnotatedString("$withdrawalString ")
    }

    var showErrorMessageDialog by remember { mutableStateOf<String?>(null) }

    if (showErrorMessageDialog != null) {
        ErrorMessageDialog(
            title = context.getString(R.string.error_dialog_pay_withdraw_error),
            textContent = showErrorMessageDialog ?: "",
            onDismiss = { showErrorMessageDialog = null }
        )
    }

    ClickableText(
        text = withdraw,
        onClick = {
            payViaIntent(withdrawalString, context) {
                showErrorMessageDialog = it
            }
        },
        style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.primary)
    )
}

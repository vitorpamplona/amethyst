package com.vitorpamplona.amethyst.ui.components

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
import androidx.compose.runtime.setValue
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
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.note.ErrorMessageDialog
import com.vitorpamplona.amethyst.ui.note.payViaIntent
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import com.vitorpamplona.quartz.encoders.LnInvoiceUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.NumberFormat

@Composable
fun MayBeInvoicePreview(lnbcWord: String) {
    var lnInvoice by remember { mutableStateOf<Pair<String, String?>?>(null) }

    LaunchedEffect(key1 = lnbcWord) {
        launch(Dispatchers.IO) {
            val myInvoice = LnInvoiceUtil.findInvoice(lnbcWord)
            if (myInvoice != null) {
                val myInvoiceAmount = try {
                    NumberFormat.getInstance().format(LnInvoiceUtil.getAmountInSats(myInvoice))
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }

                lnInvoice = Pair(myInvoice, myInvoiceAmount)
            }
        }
    }

    Crossfade(targetState = lnInvoice) {
        if (it != null) {
            InvoicePreview(it.first, it.second)
        } else {
            Text(
                text = lnbcWord,
                style = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
            )
        }
    }
}

@Composable
fun InvoicePreview(lnInvoice: String, amount: String?) {
    val context = LocalContext.current

    var showErrorMessageDialog by remember { mutableStateOf<String?>(null) }

    if (showErrorMessageDialog != null) {
        ErrorMessageDialog(
            title = context.getString(R.string.error_dialog_pay_invoice_error),
            textContent = showErrorMessageDialog ?: "",
            onDismiss = { showErrorMessageDialog = null }
        )
    }

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
                    painter = painterResource(R.drawable.lightning),
                    null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.Unspecified
                )

                Text(
                    text = stringResource(R.string.lightning_invoice),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W500,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }

            Divider()

            amount?.let {
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
                    payViaIntent(lnInvoice, context) {
                        showErrorMessageDialog = it
                    }
                },
                shape = QuoteBorder,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(text = stringResource(R.string.pay), color = Color.White, fontSize = 20.sp)
            }
        }
    }
}

/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.hashtags.Cashu
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.service.cashu.CachedCashuParser
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.note.OpenInNewIcon
import com.vitorpamplona.amethyst.ui.note.ZapIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.Size18Modifier
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.quartz.nip60Cashu.token.CashuToken
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat

@Composable
fun CashuPreview(
    cashutoken: String,
    accountViewModel: AccountViewModel,
) {
    @Suppress("ProduceStateDoesNotAssignValue")
    val cashuData by produceState(
        initialValue = CachedCashuParser.cached(cashutoken),
        key1 = cashutoken,
    ) {
        val newToken = withContext(Dispatchers.IO) { CachedCashuParser.parse(cashutoken) }
        if (value != newToken) {
            value = newToken
        }
    }

    CrossfadeIfEnabled(targetState = cashuData, label = "CashuPreview", accountViewModel = accountViewModel) {
        when (it) {
            is GenericLoadable.Loaded<ImmutableList<CashuToken>> -> {
                CashuPreview(it.loaded, accountViewModel)
            }

            is GenericLoadable.Error<ImmutableList<CashuToken>> -> {
                Text(
                    text = "$cashutoken ",
                    style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                )
            }

            else -> {}
        }
    }
}

@Composable
fun CashuPreview(
    tokens: ImmutableList<CashuToken>,
    accountViewModel: AccountViewModel,
) {
    tokens.forEach {
        CashuPreviewNew(
            it,
            accountViewModel::meltCashu,
            accountViewModel.toastManager::toast,
        )
    }
}

@Composable
@Preview()
fun CashuPreviewPreview() {
    ThemeComparisonColumn {
        CashuPreviewNew(
            token = CashuToken("token", "https://mint.example.com", 32400, listOf(), "sat", "Thanks for the coffee!"),
            melt = { _, _, _ -> },
            toast = { _, _ -> },
        )
    }
}

/**
 * Formats the token amount with its NUT-00 unit: sats stay sats, fiat units
 * (denominated in minor units, e.g. usd = cents) are shown with two decimals,
 * anything unknown is rendered verbatim rather than mislabeled as sats.
 */
private fun cashuAmountLabel(
    token: CashuToken,
    satsLabel: String,
): Pair<String, String> {
    val unit = token.unit
    return when (unit) {
        null, "sat" -> NumberFormat.getIntegerInstance().format(token.totalAmount) to satsLabel
        "usd", "eur" ->
            NumberFormat.getInstance().apply { minimumFractionDigits = 2 }.format(token.totalAmount / 100.0) to
                unit.uppercase()
        else -> NumberFormat.getIntegerInstance().format(token.totalAmount) to unit
    }
}

@Composable
fun CashuPreviewNew(
    token: CashuToken,
    melt: (CashuToken, Context, (String, String) -> Unit) -> Unit,
    toast: (String, String) -> Unit,
) {
    val context = LocalContext.current

    PaymentCard(
        title = stringRes(R.string.cashu),
        icon = {
            Icon(
                imageVector = CustomHashTagIcons.Cashu,
                contentDescription = null,
                modifier = Size18Modifier,
                tint = Color.Unspecified,
            )
        },
        copyValue = token.token,
    ) {
        val satsLabel = stringRes(R.string.sats)
        val (amount, unit) = remember(token) { cashuAmountLabel(token, satsLabel) }

        PaymentCardAmount(amount = amount, unit = unit)

        token.memo?.let {
            PaymentCardDescription(it)
        }

        Text(
            text = stringRes(R.string.cashu_mint_label, token.mint.removePrefix("https://")),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
        ) {
            var isRedeeming by remember { mutableStateOf(false) }

            Button(
                onClick = {
                    isRedeeming = true
                    melt(token, context) { title, message ->
                        toast(title, message)
                        isRedeeming = false
                    }
                },
                shape = ButtonBorder,
                modifier = Modifier.weight(1f),
            ) {
                if (isRedeeming) {
                    LoadingAnimation()
                } else {
                    ZapIcon(Size20Modifier, tint = MaterialTheme.colorScheme.onPrimary)
                }
                Spacer(StdHorzSpacer)

                Text(stringRes(R.string.cashu_redeem))
            }

            Spacer(modifier = StdHorzSpacer)

            FilledTonalButton(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, "cashu://${token.token}".toUri())
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

                        context.startActivity(intent)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        toast(stringRes(context, R.string.cashu), stringRes(context, R.string.cashu_no_wallet_found))
                    }
                },
                shape = ButtonBorder,
            ) {
                OpenInNewIcon(Size18Modifier)
            }
        }
    }
}

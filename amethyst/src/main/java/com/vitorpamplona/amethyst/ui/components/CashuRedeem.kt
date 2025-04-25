/**
 * Copyright (c) 2024 Vitor Pamplona
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.startActivity
import androidx.core.net.toUri
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.hashtags.Cashu
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.service.CachedCashuProcessor
import com.vitorpamplona.amethyst.service.CashuToken
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.note.CopyIcon
import com.vitorpamplona.amethyst.ui.note.OpenInNewIcon
import com.vitorpamplona.amethyst.ui.note.ZapIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.CashuCardBorders
import com.vitorpamplona.amethyst.ui.theme.Size18Modifier
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.SmallishBorder
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CashuPreview(
    cashutoken: String,
    accountViewModel: AccountViewModel,
) {
    @Suppress("ProduceStateDoesNotAssignValue")
    val cashuData by produceState(
        initialValue = CachedCashuProcessor.cached(cashutoken),
        key1 = cashutoken,
    ) {
        val newToken = withContext(Dispatchers.Default) { CachedCashuProcessor.parse(cashutoken) }
        if (value != newToken) {
            value = newToken
        }
    }

    CrossfadeIfEnabled(targetState = cashuData, label = "CashuPreview", accountViewModel = accountViewModel) {
        when (it) {
            is GenericLoadable.Loaded<ImmutableList<CashuToken>> -> CashuPreview(it.loaded, accountViewModel)
            is GenericLoadable.Error<ImmutableList<CashuToken>> ->
                Text(
                    text = "$cashutoken ",
                    style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                )
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
            token = CashuToken("token", "mint", 32400, listOf()),
            melt = { token, context, onDone -> },
            toast = { title, message -> },
        )
    }
}

@Composable
fun CashuPreviewNew(
    token: CashuToken,
    melt: (CashuToken, Context, (String, String) -> Unit) -> Unit,
    toast: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = CashuCardBorders,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = CustomHashTagIcons.Cashu,
                    null,
                    modifier = Modifier.size(13.dp),
                    tint = Color.Unspecified,
                )

                Text(
                    text = stringRes(R.string.cashu),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 5.dp, bottom = 1.dp),
                )
            }

            Text(
                text = "${token.totalAmount} ${stringRes(id = R.string.sats)}",
                fontSize = 20.sp,
                modifier = Modifier.padding(top = 5.dp),
            )
            Text(
                text = "Mint: " + token.mint.replace("https://", ""),
                fontSize = 7.sp,
                color = Color.Gray,
                modifier = Modifier.padding(start = 5.dp, bottom = 1.dp),
            )

            Row(modifier = Modifier.padding(top = 5.dp)) {
                var isRedeeming by remember { mutableStateOf(false) }

                FilledTonalButton(
                    onClick = {
                        isRedeeming = true
                        melt(token, context) { title, message ->
                            toast(title, message)
                            isRedeeming = false
                        }
                    },
                    shape = SmallishBorder,
                ) {
                    if (isRedeeming) {
                        LoadingAnimation()
                    } else {
                        ZapIcon(Size20Modifier, tint = MaterialTheme.colorScheme.onBackground)
                    }
                    Spacer(StdHorzSpacer)

                    Text(
                        "Redeem",
                        fontSize = 16.sp,
                    )
                }

                Spacer(modifier = StdHorzSpacer)

                FilledTonalButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, "cashu://${token.token}".toUri())
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

                            startActivity(context, intent, null)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            toast(stringRes(context, R.string.cashu), stringRes(context, R.string.cashu_no_wallet_found))
                        }
                    },
                    shape = SmallishBorder,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    OpenInNewIcon(Size18Modifier)
                }

                Spacer(modifier = StdHorzSpacer)

                FilledTonalButton(
                    onClick = {
                        // Copying the token to clipboard
                        clipboardManager.setText(AnnotatedString(token.token))
                    },
                    shape = SmallishBorder,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    CopyIcon(Size18Modifier)
                }
            }
        }
    }
}

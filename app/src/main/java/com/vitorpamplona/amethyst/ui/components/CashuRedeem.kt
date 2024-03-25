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
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fasterxml.jackson.databind.node.TextNode
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.hashtags.Cashu
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.model.ThemeType
import com.vitorpamplona.amethyst.service.CachedCashuProcessor
import com.vitorpamplona.amethyst.service.CashuToken
import com.vitorpamplona.amethyst.ui.actions.LoadingAnimation
import com.vitorpamplona.amethyst.ui.note.CashuIcon
import com.vitorpamplona.amethyst.ui.note.CopyIcon
import com.vitorpamplona.amethyst.ui.note.OpenInNewIcon
import com.vitorpamplona.amethyst.ui.note.ZapIcon
import com.vitorpamplona.amethyst.ui.screen.SharedPreferencesViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.AmethystTheme
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.Size18Modifier
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.SmallishBorder
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CashuPreview(
    cashutoken: String,
    accountViewModel: AccountViewModel,
) {
    val cashuData by produceState(
        initialValue = CachedCashuProcessor.cached(cashutoken),
        key1 = cashutoken,
    ) {
        withContext(Dispatchers.IO) {
            val newToken = CachedCashuProcessor.parse(cashutoken)
            if (value != newToken) {
                value = newToken
            }
        }
    }

    Crossfade(targetState = cashuData, label = "CashuPreview") {
        when (it) {
            is GenericLoadable.Loaded<CashuToken> -> CashuPreview(it.loaded, accountViewModel)
            is GenericLoadable.Error<CashuToken> ->
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
    token: CashuToken,
    accountViewModel: AccountViewModel,
) {
    CashuPreviewNew(token, accountViewModel::meltCashu, accountViewModel::toast)
}

@Composable
@Preview()
fun CashuPreviewPreview() {
    val sharedPreferencesViewModel: SharedPreferencesViewModel = viewModel()

    sharedPreferencesViewModel.init()
    sharedPreferencesViewModel.updateTheme(ThemeType.DARK)

    AmethystTheme(sharedPrefsViewModel = sharedPreferencesViewModel) {
        Column {
            CashuPreview(
                token = CashuToken("token", "mint", 32400, TextNode("")),
                melt = { token, context, onDone -> },
                toast = { title, message -> },
            )

            CashuPreviewNew(
                token = CashuToken("token", "mint", 32400, TextNode("")),
                melt = { token, context, onDone -> },
                toast = { title, message -> },
            )
        }
    }
}

@Composable
fun CashuPreview(
    token: CashuToken,
    melt: (CashuToken, Context, (String, String) -> Unit) -> Unit,
    toast: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 10.dp)
                .clip(shape = QuoteBorder)
                .border(1.dp, MaterialTheme.colorScheme.subtleBorder, QuoteBorder),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
            ) {
                Icon(
                    imageVector = CustomHashTagIcons.Cashu,
                    null,
                    modifier = Size20Modifier,
                    tint = Color.Unspecified,
                )

                Text(
                    text = stringResource(R.string.cashu),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W500,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }

            HorizontalDivider(thickness = DividerThickness)

            Text(
                text = "${token.totalAmount} ${stringResource(id = R.string.sats)}",
                fontSize = 25.sp,
                fontWeight = FontWeight.W500,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
            )

            Row(
                modifier =
                    Modifier
                        .padding(top = 5.dp)
                        .fillMaxWidth(),
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
                    shape = QuoteBorder,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                ) {
                    if (isRedeeming) {
                        LoadingAnimation()
                    } else {
                        ZapIcon(Size20Modifier, tint = Color.White)
                    }
                    Spacer(DoubleHorzSpacer)

                    Text(
                        stringResource(id = R.string.cashu_redeem_to_zap),
                        color = Color.White,
                        fontSize = 16.sp,
                    )
                }
            }

            Spacer(modifier = StdHorzSpacer)
            Button(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("cashu://${token.token}"))
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

                        startActivity(context, intent, null)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        toast("Cashu", context.getString(R.string.cashu_no_wallet_found))
                    }
                },
                shape = QuoteBorder,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
            ) {
                CashuIcon(Size20Modifier)
                Spacer(DoubleHorzSpacer)
                Text(
                    stringResource(id = R.string.cashu_redeem_to_cashu),
                    color = Color.White,
                    fontSize = 16.sp,
                )
            }
            Spacer(modifier = StdHorzSpacer)
            Button(
                onClick = {
                    // Copying the token to clipboard
                    clipboardManager.setText(AnnotatedString(token.token))
                },
                shape = QuoteBorder,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
            ) {
                CopyIcon(Size20Modifier, Color.White)
                Spacer(DoubleHorzSpacer)
                Text(stringResource(id = R.string.cashu_copy_token), color = Color.White, fontSize = 16.sp)
            }
            Spacer(modifier = StdHorzSpacer)
        }
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
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
                .clip(shape = QuoteBorder),
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
                    text = stringResource(R.string.cashu),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 5.dp, bottom = 1.dp),
                )
            }

            Text(
                text = "${token.totalAmount} ${stringResource(id = R.string.sats)}",
                fontSize = 20.sp,
                modifier = Modifier.padding(top = 5.dp),
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
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 16.sp,
                    )
                }

                Spacer(modifier = StdHorzSpacer)

                FilledTonalButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("cashu://${token.token}"))
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

                            startActivity(context, intent, null)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            toast("Cashu", context.getString(R.string.cashu_no_wallet_found))
                        }
                    },
                    shape = SmallishBorder,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    OpenInNewIcon(Size18Modifier, tint = MaterialTheme.colorScheme.onBackground)
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
                    CopyIcon(Size18Modifier, tint = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    }
}

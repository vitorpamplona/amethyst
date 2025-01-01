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
package com.vitorpamplona.amethyst.ui.note.elements

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.ZapPaymentHandler
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.components.ClickableText
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.navigation.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.routeFor
import com.vitorpamplona.amethyst.ui.note.CloseIcon
import com.vitorpamplona.amethyst.ui.note.MultiUserErrorMessageDialog
import com.vitorpamplona.amethyst.ui.note.ObserveZapIcon
import com.vitorpamplona.amethyst.ui.note.PayViaIntentDialog
import com.vitorpamplona.amethyst.ui.note.UserBasedErrorMessageViewModel
import com.vitorpamplona.amethyst.ui.note.ZapAmountChoicePopup
import com.vitorpamplona.amethyst.ui.note.ZapIcon
import com.vitorpamplona.amethyst.ui.note.ZappedIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ModifierWidth3dp
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.imageModifier
import com.vitorpamplona.quartz.events.Event
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@Preview
@Composable
fun ZapTheDevsCardPreview() {
    runBlocking(Dispatchers.IO) {
        val releaseNotes =
            """
            {
              "id": "0465b20da0adf45dd612024d124e1ed384f7ecd2cd7358e77998828e7bf35fa2",
              "pubkey": "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
              "created_at": 1708014675,
              "kind": 1,
              "tags": [
                [
                  "p",
                  "ca89cb11f1c75d5b6622268ff43d2288ea8b2cb5b9aa996ff9ff704fc904b78b",
                  "",
                  "mention"
                ],
                [
                  "p",
                  "7eb29c126b3628077e2e3d863b917a56b74293aa9d8a9abc26a40ba3f2866baf",
                  "",
                  "mention"
                ],
                [
                  "t",
                  "Amethyst"
                ],
                [
                  "t",
                  "amethyst"
                ],
                [
                  "zap",
                  "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
                  "wss://vitor.nostr1.com",
                  "0.6499999761581421"
                ],
                [
                  "zap",
                  "ca89cb11f1c75d5b6622268ff43d2288ea8b2cb5b9aa996ff9ff704fc904b78b",
                  "wss://nos.lol",
                  "0.25"
                ],
                [
                  "zap",
                  "7eb29c126b3628077e2e3d863b917a56b74293aa9d8a9abc26a40ba3f2866baf",
                  "wss://vitor.nostr1.com",
                  "0.10000000149011612"
                ],
                [
                  "r",
                  "https://github.com/vitorpamplona/amethyst/releases/download/v0.84.2/amethyst-googleplay-universal-v0.84.2.apk"
                ],
                [
                  "r",
                  "https://github.com/vitorpamplona/amethyst/releases/download/v0.84.2/amethyst-fdroid-universal-v0.84.2.apk"
                ]
              ],
              "content": "#Amethyst v0.84.2: Text alignment fix\n\nBugfixes:\n- Fixes link misalignment in posts\n\nUpdated translations: \n- Czech, German, Swedish, and Portuguese by nostr:npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef\n- French by nostr:npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz\n\nDownload:\n- [Play Edition](https://github.com/vitorpamplona/amethyst/releases/download/v0.84.2/amethyst-googleplay-universal-v0.84.2.apk )\n- [FOSS Edition - No translations](https://github.com/vitorpamplona/amethyst/releases/download/v0.84.2/amethyst-fdroid-universal-v0.84.2.apk )",
              "sig": "e036ecce534e22efd47634c56328af62576ab3a36c565f7c8c5fbea67f48cd46d4041ecfc0ca01dafa0ebe8a0b119d125527a28f88aa30356b80c26dd0953aed"
            }
            """.trimIndent()

        LocalCache.justConsume(Event.fromJson(releaseNotes), null)
    }

    val accountViewModel = mockAccountViewModel()

    LoadNote(
        baseNoteHex = "0465b20da0adf45dd612024d124e1ed384f7ecd2cd7358e77998828e7bf35fa2",
        accountViewModel,
    ) { releaseNote ->
        if (releaseNote != null) {
            ThemeComparisonColumn {
                ZapTheDevsCard(
                    releaseNote,
                    accountViewModel,
                    nav = EmptyNav,
                )
            }
        }
    }
}

@Composable
fun ZapTheDevsCard(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val releaseNoteState by baseNote.live().metadata.observeAsState()
    val releaseNote = releaseNoteState?.note ?: return

    Row(modifier = Modifier.padding(start = Size10dp, end = Size10dp, bottom = Size10dp)) {
        Card(
            modifier = MaterialTheme.colorScheme.imageModifier,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                // Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringRes(id = R.string.zap_the_devs_title),
                        style =
                            TextStyle(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                    )

                    IconButton(
                        modifier = Size20Modifier,
                        onClick = { accountViewModel.markDonatedInThisVersion() },
                    ) {
                        CloseIcon()
                    }
                }

                Spacer(modifier = StdVertSpacer)

                ClickableText(
                    text =
                        buildAnnotatedString {
                            append(stringRes(id = R.string.zap_the_devs_description, BuildConfig.VERSION_NAME))
                            append(" ")
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                append("#value4value")
                            }
                        },
                    onClick = { nav.nav("Hashtag/value4value") },
                )

                Spacer(modifier = StdVertSpacer)

                val noteEvent = releaseNote.event
                if (noteEvent != null) {
                    val route =
                        remember(releaseNote) {
                            routeFor(releaseNote, accountViewModel.userProfile())
                        }

                    if (route != null) {
                        ClickableText(
                            text =
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                        append(stringRes(id = R.string.version_name, BuildConfig.VERSION_NAME.substringBefore("-")))
                                    }
                                    append(" " + stringRes(id = R.string.brought_to_you_by))
                                },
                            onClick = { nav.nav(route) },
                        )
                    } else {
                        Text(stringRes(id = R.string.this_version_brought_to_you_by))
                    }

                    Spacer(modifier = StdVertSpacer)

                    DisplayZapSplits(
                        noteEvent = noteEvent,
                        useAuthorIfEmpty = true,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )

                    Spacer(modifier = StdVertSpacer)
                }

                ZapDonationButton(
                    baseNote = releaseNote,
                    grayTint = MaterialTheme.colorScheme.onPrimary,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

@Composable
fun ZapDonationButton(
    baseNote: Note,
    grayTint: Color,
    accountViewModel: AccountViewModel,
    iconSize: Dp = Size35dp,
    iconSizeModifier: Modifier = Size20Modifier,
    animationSize: Dp = 14.dp,
    nav: INav,
) {
    var wantsToZap by remember { mutableStateOf<ImmutableList<Long>?>(null) }
    val errorViewModel: UserBasedErrorMessageViewModel = viewModel()
    var wantsToPay by
        remember(baseNote) {
            mutableStateOf<ImmutableList<ZapPaymentHandler.Payable>>(
                persistentListOf(),
            )
        }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var zappingProgress by remember { mutableFloatStateOf(0f) }
    var hasZapped by remember { mutableStateOf(false) }

    Button(
        onClick = {
            customZapClick(
                baseNote,
                accountViewModel,
                context,
                onZappingProgress = { progress: Float ->
                    scope.launch { zappingProgress = progress }
                },
                onMultipleChoices = { options -> wantsToZap = options.toImmutableList() },
                onError = { _, message, toUser ->
                    scope.launch {
                        zappingProgress = 0f
                        errorViewModel.add(message, toUser)
                    }
                },
                onPayViaIntent = { wantsToPay = it },
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        wantsToZap?.let {
            ZapAmountChoicePopup(
                baseNote = baseNote,
                zapAmountChoices = it,
                popupYOffset = iconSize,
                accountViewModel = accountViewModel,
                onDismiss = {
                    wantsToZap = null
                    zappingProgress = 0f
                },
                onChangeAmount = {
                    wantsToZap = null
                },
                onError = { _, message, user ->
                    scope.launch {
                        zappingProgress = 0f
                        errorViewModel.add(message, user)
                    }
                },
                onProgress = {
                    scope.launch(Dispatchers.Main) { zappingProgress = it }
                },
                onPayViaIntent = { wantsToPay = it },
            )
        }

        MultiUserErrorMessageDialog(
            title = stringRes(id = R.string.error_dialog_zap_error),
            model = errorViewModel,
            accountViewModel = accountViewModel,
            nav = nav,
        )

        if (wantsToPay.isNotEmpty()) {
            PayViaIntentDialog(
                payingInvoices = wantsToPay,
                accountViewModel = accountViewModel,
                onClose = { wantsToPay = persistentListOf() },
                onError = {
                    wantsToPay = persistentListOf()
                    scope.launch {
                        zappingProgress = 0f
                        errorViewModel.add(it)
                    }
                },
                justShowError = {
                    scope.launch {
                        errorViewModel.add(it)
                    }
                },
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = iconSizeModifier,
        ) {
            if (zappingProgress > 0.00001 && zappingProgress < 0.99999) {
                Spacer(ModifierWidth3dp)

                CircularProgressIndicator(
                    progress =
                        animateFloatAsState(
                            targetValue = zappingProgress,
                            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                            label = "ZapIconIndicator",
                        ).value,
                    modifier = remember { Modifier.size(animationSize) },
                    strokeWidth = 2.dp,
                    color = grayTint,
                )
            } else {
                ObserveZapIcon(
                    baseNote,
                    accountViewModel,
                ) { wasZappedByLoggedInUser ->
                    LaunchedEffect(wasZappedByLoggedInUser.value) {
                        hasZapped = wasZappedByLoggedInUser.value
                        if (wasZappedByLoggedInUser.value && !accountViewModel.account.hasDonatedInThisVersion()) {
                            delay(1000)
                            accountViewModel.markDonatedInThisVersion()
                        }
                    }

                    CrossfadeIfEnabled(targetState = wasZappedByLoggedInUser.value, label = "ZapIcon", accountViewModel = accountViewModel) {
                        if (it) {
                            ZappedIcon(iconSizeModifier)
                        } else {
                            ZapIcon(iconSizeModifier, grayTint)
                        }
                    }
                }
            }
        }

        if (hasZapped) {
            Text(text = stringRes(id = R.string.thank_you))
        } else {
            Text(text = stringRes(id = R.string.donate_now))
        }
    }
}

fun customZapClick(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    context: Context,
    onZappingProgress: (Float) -> Unit,
    onMultipleChoices: (List<Long>) -> Unit,
    onError: (String, String, User?) -> Unit,
    onPayViaIntent: (ImmutableList<ZapPaymentHandler.Payable>) -> Unit,
) {
    if (baseNote.isDraft()) {
        accountViewModel.toast(
            R.string.draft_note,
            R.string.it_s_not_possible_to_zap_to_a_draft_note,
        )
        return
    }

    val choices = accountViewModel.zapAmountChoices()

    if (choices.isEmpty()) {
        accountViewModel.toast(
            stringRes(context, R.string.error_dialog_zap_error),
            stringRes(context, R.string.no_zap_amount_setup_long_press_to_change),
        )
    } else if (!accountViewModel.isWriteable()) {
        accountViewModel.toast(
            stringRes(context, R.string.error_dialog_zap_error),
            stringRes(context, R.string.login_with_a_private_key_to_be_able_to_send_zaps),
        )
    } else if (choices.size == 1) {
        val amount = choices.first()

        if (amount > 1100) {
            accountViewModel.zap(
                baseNote,
                amount * 1000,
                null,
                "",
                context,
                showErrorIfNoLnAddress = false,
                onError = onError,
                onProgress = { onZappingProgress(it) },
                onPayViaIntent = onPayViaIntent,
            )
        } else {
            onMultipleChoices(listOf(1000L, 5_000L, 10_000L))
            // recommends amounts for a monthly release.
        }
    } else if (choices.size > 1) {
        if (choices.any { it > 1100 }) {
            onMultipleChoices(choices)
        } else {
            onMultipleChoices(listOf(1000L, 5_000L, 10_000L))
        }
    }
}

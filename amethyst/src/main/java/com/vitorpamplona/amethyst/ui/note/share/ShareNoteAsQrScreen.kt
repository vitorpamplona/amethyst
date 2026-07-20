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
package com.vitorpamplona.amethyst.ui.note.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNote
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.components.getActivityWindow
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.QrCodeDrawer
import com.vitorpamplona.amethyst.ui.stringRes

// A cap, not a fixed size: QrCodeDrawer's own quiet zone (QR_MARGIN_PX in QrCodeDrawer.kt) is a
// fixed pixel count subtracted from raw size.width, so its share of the tile grows as density
// falls. Hard-sizing this call to a small dp value starved long-form naddr payloads of scannable
// resolution on low-density screens. Deriving the size from the available column width keeps
// enough real pixels per module; this only bounds it from growing unreasonably large on tablets.
private val QrMaxSize = 320.dp

/**
 * Display-only screen presenting [id]'s note as a scannable QR code.
 *
 * There is no export or save action by design — the screen exists to be held up and
 * photographed by another device.
 *
 * F6: the Scaffold (and its back button) lives in this id-based wrapper, OUTSIDE LoadNote's
 * null check, so an id that never resolves to a note still leaves the user a way back — only
 * the body inside is empty in that case. Rendering nothing else for an unresolved id is
 * deliberate, matching ShareNoteAsImageScreen; only the missing chrome was the bug.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareNoteAsQrScreen(
    id: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(R.string.share_as_qr), nav) },
    ) { pad ->
        LoadNote(id, accountViewModel) { note ->
            if (note != null) {
                ShareNoteAsQrScreenContent(note, accountViewModel, nav, pad)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareNoteAsQrScreen(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(R.string.share_as_qr), nav) },
    ) { pad ->
        ShareNoteAsQrScreenContent(note, accountViewModel, nav, pad)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareNoteAsQrScreenContent(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
    pad: PaddingValues,
) {
    var mode by remember { mutableStateOf(QrPayloadMode.Web) }

    // F4: keyed on the observed note state, not on `note` alone (a stable object identity that
    // does not change while the event and the author's relay list are still loading). A payload
    // computed before then would omit the relay hint (Note.relayHintUrl()) and never recompute.
    // Keying on `noteState` re-derives the payload once the event arrives — the same observation
    // SharedNoteCard uses for its own sensitivity gate (F1).
    val noteState by observeNote(note, accountViewModel)
    val payload = remember(noteState, mode) { qrPayloadFor(note, mode) }

    KeepScreenBrightAndAwake()

    // F5: scrollable so the toggle and hint — the screen's only controls — stay reachable on a
    // short viewport (landscape, split screen, large font scale) where the square QR plus the
    // card above it can otherwise exceed the available height. A plain fillMaxSize() Column would
    // silently place that overflow outside its bounds instead of clipping or scrolling to it.
    Column(
        modifier =
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SharedNoteCard(note, accountViewModel, nav)

        val qrContentDescription =
            when (mode) {
                QrPayloadMode.Web -> stringRes(R.string.share_as_qr_code_description_web)
                QrPayloadMode.Nostr -> stringRes(R.string.share_as_qr_code_description_nostr)
            }
        QrCodeDrawer(
            contents = payload,
            modifier =
                Modifier
                    .widthIn(max = QrMaxSize)
                    .fillMaxWidth()
                    .semantics { contentDescription = qrContentDescription },
        )

        SingleChoiceSegmentedButtonRow {
            val modes = listOf(QrPayloadMode.Web, QrPayloadMode.Nostr)
            modes.forEachIndexed { index, candidate ->
                SegmentedButton(
                    selected = mode == candidate,
                    onClick = { mode = candidate },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                ) {
                    Text(
                        when (candidate) {
                            QrPayloadMode.Web -> stringRes(R.string.share_as_qr_mode_web)
                            QrPayloadMode.Nostr -> stringRes(R.string.share_as_qr_mode_nostr)
                        },
                    )
                }
            }
        }

        Text(
            text =
                when (mode) {
                    QrPayloadMode.Web -> stringRes(R.string.share_as_qr_hint_web)
                    QrPayloadMode.Nostr -> stringRes(R.string.share_as_qr_hint_nostr)
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Raises the screen to full brightness and prevents it sleeping while the QR is displayed,
 * restoring both on exit.
 *
 * This is functional, not polish: the screen exists to be photographed, and a dark-theme phone
 * on auto-brightness in a dim room is exactly the case that fails.
 *
 * Residual hazard, not reachable today: [LocalView.current] is the Activity's single shared root
 * `ComposeView`, not a view scoped to this screen. During a nav transition two compositions can
 * briefly coexist on that same root view, so this screen's `onDispose` could in theory clobber
 * brightness/`keepScreenOn` state an incoming screen has already set. Nothing in the current nav
 * graph triggers that overlap, so this is left as a comment rather than code.
 */
@Composable
private fun KeepScreenBrightAndAwake() {
    val view = LocalView.current
    // NOT `(view.context as? Activity)`: under Compose the context is routinely a
    // ContextThemeWrapper, so that cast silently yields null and brightness never changes —
    // no crash, no log, just a dead feature. getActivityWindow() unwraps the ContextWrapper
    // chain (WindowUtils.kt:39-46).
    val window = getActivityWindow()

    DisposableEffect(window, view) {
        // Capture the RAW attribute, not a computed fraction. When no override is set this is
        // BRIGHTNESS_OVERRIDE_NONE (-1f), and restoring that value returns the device to auto
        // brightness. Restoring a *computed* fraction would install an override where none
        // existed and silently disable auto-brightness for the rest of the session.
        val previousBrightness = window?.attributes?.screenBrightness

        // F8: same capture/replay discipline as brightness above, and for the same reason.
        // `view` is the Activity's single shared root ComposeView, and PlayerEventListener
        // (ControlWhenPlayerIsActive.kt:150-165) owns this exact flag while media plays.
        // Hard-setting `false` on dispose — instead of restoring what was here before this
        // screen took it over — would clobber that ownership: navigating back from the QR
        // screen while audio or video is still playing would let the screen sleep mid-playback.
        val previousKeepScreenOn = view.keepScreenOn

        window?.let {
            it.attributes = it.attributes.apply { screenBrightness = 1f }
        }
        view.keepScreenOn = true

        onDispose {
            // Restore the captured value rather than calling a release helper: resetting to
            // BRIGHTNESS_OVERRIDE_NONE unconditionally would clobber an override the user
            // already had, e.g. one left by the fullscreen video controls.
            window?.let { w ->
                previousBrightness?.let { prev ->
                    w.attributes = w.attributes.apply { screenBrightness = prev }
                }
            }
            view.keepScreenOn = previousKeepScreenOn
        }
    }
}

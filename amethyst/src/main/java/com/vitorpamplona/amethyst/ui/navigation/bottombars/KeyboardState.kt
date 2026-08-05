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
package com.vitorpamplona.amethyst.ui.navigation.bottombars

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

enum class KeyboardState {
    Opened,
    Closed,
}

/**
 * Whether the soft keyboard is currently on screen, derived from [WindowInsets.ime].
 *
 * This intentionally reads the same animated IME inset that drives `Modifier.imePadding()`
 * everywhere else in the app, so the two can never disagree. The previous implementation
 * measured `View.getWindowVisibleDisplayFrame` from a `ViewTreeObserver` global-layout
 * listener — a pre-edge-to-edge heuristic. Under `enableEdgeToEdge()`
 * (`decorFitsSystemWindows = false`) the window content no longer resizes for the IME, so
 * that listener fired when the keyboard appeared but frequently never fired again when it
 * closed, latching the state at [Opened] even though the keyboard was gone (leaving the
 * bottom navigation bar hidden and a stranded gap at the bottom). The IME inset always
 * animates back to zero on the persistent root view, so this reading can't get stuck.
 */
@Composable
fun keyboardAsState(): State<KeyboardState> {
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    return remember(density, imeInsets) {
        derivedStateOf {
            if (imeInsets.getBottom(density) > 0) KeyboardState.Opened else KeyboardState.Closed
        }
    }
}

/** How long to wait for the IME inset to reach zero before running the action anyway. */
private const val IME_SETTLE_TIMEOUT_MS = 700L

/**
 * Returns a runner that defers an action until the soft keyboard is fully off screen.
 *
 * Popping a screen while the keyboard is still up races the window animation against the IME's
 * close animation. On release builds — fast enough that the window animation wins — the IME
 * [WindowInsetsAnimationCompat][androidx.core.view.WindowInsetsAnimationCompat] is cancelled before
 * its terminal (zero) frame reaches Compose, so the shared `WindowInsets.ime` holder stays
 * "animating" and every `Modifier.imePadding()` in the app freezes at the keyboard height until a
 * later inset pass rebalances it (the "stuck IME padding" that survives leaving the screen).
 *
 * Any exit that leaves a keyboard-bearing screen has to serialize the two animations rather than
 * overlap them. With the keyboard already down the action runs inline — same frame, no behavior
 * change. With it up we dismiss the keyboard, wait for the inset to actually reach zero, and only
 * then act, so the IME animation always completes before the window animation begins.
 *
 * Re-entrant calls while an action is pending are dropped: the deferral widens the window in which
 * a second tap on a Post/Save button would fire the action twice.
 *
 * [IME_SETTLE_TIMEOUT_MS] bounds the wait — if the inset never reports zero (precisely the failure
 * this guards against) the action still runs, so a stale reading can never trap the user on screen.
 */
@Composable
fun rememberAfterKeyboardCloses(): (() -> Unit) -> Unit {
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val pending = remember { AtomicBoolean(false) }

    return remember(density, imeInsets, keyboard, focusManager, scope, pending) {
        { action: () -> Unit ->
            if (imeInsets.getBottom(density) <= 0) {
                action()
            } else if (pending.compareAndSet(false, true)) {
                // Clear focus first so nothing re-requests the IME as it retracts.
                focusManager.clearFocus(true)
                keyboard?.hide()
                scope.launch {
                    try {
                        withTimeoutOrNull(IME_SETTLE_TIMEOUT_MS) {
                            snapshotFlow { imeInsets.getBottom(density) }.first { it <= 0 }
                        }
                        action()
                    } finally {
                        pending.set(false)
                    }
                }
            }
        }
    }
}

/**
 * A [BackHandler] that lets the system dismiss the soft keyboard before it consumes back.
 *
 * Chat composers (and draft-saving editors) intercept back to flush a draft and pop the screen,
 * which is the pop-during-IME-animation race described on [rememberAfterKeyboardCloses]. While the
 * keyboard is up we do NOT consume back, so the system dismisses it first with its own animation
 * (which completes cleanly, and on recent Android follows the back gesture). The next back runs
 * [onBack] as before.
 *
 * The gate reads [WindowInsets.imeAnimationTarget] — where the IME is *heading* — not the animated
 * [WindowInsets.ime]. Gating on the animated value left a hole: it stays above zero for the whole
 * close animation, ~250ms in which the IME has already stopped consuming back but this handler was
 * still disabled, so a second back fell through to the NavController and popped the screen without
 * ever running [onBack] — silently dropping the draft it exists to save. The target flips to zero
 * the moment the hide begins, so back keeps reaching [onBack] throughout.
 *
 * Re-enabling that early means [onBack] can now fire mid-animation, so it is routed through
 * [rememberAfterKeyboardCloses] to wait for the inset to settle before popping.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyboardAwareBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
) {
    val density = LocalDensity.current
    val imeTarget = WindowInsets.imeAnimationTarget
    val afterKeyboardCloses = rememberAfterKeyboardCloses()

    val keyboardIsStaying by remember(density, imeTarget) {
        derivedStateOf { imeTarget.getBottom(density) > 0 }
    }

    BackHandler(enabled = enabled && !keyboardIsStaying) { afterKeyboardCloses(onBack) }
}

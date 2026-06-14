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
package com.vitorpamplona.amethyst.ui.screen

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.service.applock.AppLockState
import com.vitorpamplona.amethyst.ui.note.authenticate
import com.vitorpamplona.amethyst.ui.stringRes

/**
 * Full-screen gate drawn on top of the whole app. When [AppLockState] reports the
 * app as locked it covers everything below it with an opaque lock screen that
 * demands the device's biometrics or PIN before the content is revealed again.
 *
 * Mount this as the last child of a `Box` in the activity root so it overlays the
 * login screen and the logged-in content alike.
 */
@Composable
fun AppLockGate() {
    val locked by AppLockState.isLocked.collectAsStateWithLifecycle()

    if (locked) {
        AppLockScreen()
    }
}

@Composable
private fun AppLockScreen() {
    val context = LocalContext.current

    // Device-credential (PIN/pattern/password) fallback launches a separate
    // activity; unlock only on a confirmed result.
    val keyguardLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                AppLockState.unlock()
            }
        }

    fun prompt() {
        // Reuses the shared biometric-or-keyguard helper: BIOMETRIC_STRONG with a
        // device-credential (PIN) fallback, and an auto-approve when the device has
        // no secure lock configured so the user can't be stranded.
        authenticate(
            title = stringRes(context, R.string.app_lock_unlock_subtitle),
            context = context,
            keyguardLauncher = keyguardLauncher,
            onApproved = { AppLockState.unlock() },
            onError = { _, _ -> },
        )
    }

    LaunchedEffect(Unit) { prompt() }

    Surface(
        modifier =
            Modifier
                .fillMaxSize()
                // Swallow every pointer event so the content drawn underneath this
                // overlay can't be tapped/scrolled through the lock screen.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                },
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                symbol = MaterialSymbols.Lock,
                contentDescription = stringRes(R.string.app_lock_title),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = stringRes(R.string.app_lock_screen_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 24.dp),
            )
            Button(
                onClick = { prompt() },
                modifier = Modifier.padding(top = 24.dp),
            ) {
                Text(stringRes(R.string.app_lock_unlock))
            }
        }
    }
}

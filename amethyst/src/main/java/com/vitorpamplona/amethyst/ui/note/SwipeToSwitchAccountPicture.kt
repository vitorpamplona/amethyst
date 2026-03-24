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
package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.ui.screen.AccountSessionManager
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

private const val SWIPE_THRESHOLD = 50f

@Composable
fun SwipeToSwitchAccountPicture(
    size: Dp,
    accountViewModel: AccountViewModel,
    accountSessionManager: AccountSessionManager,
) {
    val accounts by LocalPreferences.accountsFlow().collectAsStateWithLifecycle()
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    val swipeModifier =
        Modifier.pointerInput(accounts) {
            detectVerticalDragGestures(
                onDragStart = { dragAccumulator = 0f },
                onVerticalDrag = { change, dragAmount ->
                    dragAccumulator += dragAmount
                    val accountList = accounts ?: return@detectVerticalDragGestures

                    if (accountList.size <= 1) return@detectVerticalDragGestures

                    if (kotlin.math.abs(dragAccumulator) > SWIPE_THRESHOLD) {
                        val currentNpub = accountViewModel.account.userProfile().pubkeyNpub()
                        val currentIndex = accountList.indexOfFirst { it.npub == currentNpub }
                        if (currentIndex < 0) return@detectVerticalDragGestures

                        val nextIndex =
                            if (dragAccumulator > 0) {
                                (currentIndex + 1) % accountList.size
                            } else {
                                (currentIndex - 1 + accountList.size) % accountList.size
                            }

                        change.consume()
                        dragAccumulator = 0f
                        accountSessionManager.switchUser(accountList[nextIndex])
                    }
                },
            )
        }

    Box(modifier = swipeModifier) {
        BaseUserPicture(
            accountViewModel.userProfile(),
            size,
            accountViewModel = accountViewModel,
        )
    }
}

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
package com.vitorpamplona.amethyst.desktop.napplet

import com.vitorpamplona.amethyst.commons.napplet.NappletConsentInfo
import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The consent questions napplets are waiting on. The broker's consent prompt suspends in [request];
 * the napplet's window shows the oldest question for its coordinate and answers it. A window that
 * closes first answers [GrantState.ASK] (not now), which grants nothing.
 */
class DesktopNappletConsent {
    class Request(
        val coordinate: String,
        val info: NappletConsentInfo,
    ) {
        internal val answer = CompletableDeferred<GrantState>()

        fun answer(decision: GrantState) {
            answer.complete(decision)
        }
    }

    private val mutablePending = MutableStateFlow<List<Request>>(emptyList())
    val pending: StateFlow<List<Request>> = mutablePending.asStateFlow()

    suspend fun request(
        coordinate: String,
        info: NappletConsentInfo,
    ): GrantState {
        val request = Request(coordinate, info)
        mutablePending.update { it + request }
        try {
            return request.answer.await()
        } finally {
            mutablePending.update { it - request }
        }
    }

    /** Answers "not now" for every question a closed napplet left behind. */
    fun dismissAll(coordinate: String) {
        pending.value.filter { it.coordinate == coordinate }.forEach { it.answer(GrantState.ASK) }
    }
}

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
package com.vitorpamplona.amethyst.commons.napplet.ui

import com.vitorpamplona.amethyst.commons.napplet.session.NappletFailure
import com.vitorpamplona.amethyst.commons.napplet.session.NappletSessionState
import com.vitorpamplona.amethyst.commons.ui.platform.InlineNappletStatus

/**
 * What a run inside a note says about itself, from its session's [state]: Tor said apart from other
 * failures, so the note can say why nothing is showing, and showing only once its page [painted].
 * The same on every host that runs the shared session core inline (Desktop, iOS).
 */
fun inlineStatusOf(
    state: NappletSessionState,
    painted: Boolean,
): InlineNappletStatus =
    when {
        state is NappletSessionState.WaitingForTor -> InlineNappletStatus.WAITING_FOR_TOR
        state is NappletSessionState.Failed && state.reason == NappletFailure.ROUTE_BLOCKED -> InlineNappletStatus.NO_TOR
        state is NappletSessionState.Failed -> InlineNappletStatus.FAILED
        state is NappletSessionState.Showing && painted -> InlineNappletStatus.SHOWING
        else -> InlineNappletStatus.LOADING
    }

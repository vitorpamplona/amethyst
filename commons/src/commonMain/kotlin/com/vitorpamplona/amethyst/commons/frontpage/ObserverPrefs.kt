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
package com.vitorpamplona.amethyst.commons.frontpage

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.SyncedSchedule

/**
 * Whether an account's editions go to its public Observer site. On by default, but nothing is
 * published until the reader has seen, once, that the site is public ([noticeSeen]): the screen
 * asks before the first print, and an unattended print never publishes before that.
 *
 * Kept in the account's synced schedule as the Observer's `publish` param
 * ([FrontPageParams.publishing]), so every device of the account follows one choice.
 */
@Immutable
data class ObserverPrefs(
    val publish: Boolean = true,
    val noticeSeen: Boolean = false,
) {
    val publishes get() = publish && noticeSeen
}

/** The publishing choice this schedule holds; the default (on, not confirmed) while it can't be read. */
fun SyncedSchedule.observerPrefs(): ObserverPrefs = FrontPageParams.publishing((this as? SyncedSchedule.Readable)?.task)

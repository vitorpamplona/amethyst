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
package com.vitorpamplona.amethyst.desktop.app

import com.vitorpamplona.amethyst.commons.account.AccountInfo
import com.vitorpamplona.amethyst.desktop.app.DesktopAccountsNotified.Companion.signsWithoutAsking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every saved account is notified, so each one loads in the background: only those that can sign
 * without asking the user, since a prompt per account at startup would come out of nowhere.
 */
class DesktopAccountsNotifiedTest {
    private val npub = "npub1sg6plzptd64u62a878hep2kev88swjh3tw00gjsfl8f237lmu63q0uf63m"

    @Test
    fun aKeptKeyOrARemoteSignerLoadsInTheBackground() {
        assertTrue(AccountInfo(npub, hasPrivKey = true).signsWithoutAsking())
        assertTrue(AccountInfo(npub, loggedInWithRemoteSigner = true).signsWithoutAsking())
    }

    @Test
    fun anAccountThatWouldPromptOrCannotSignWaitsToBeOpened() {
        assertFalse(AccountInfo(npub, hasPrivKey = true, keyRemembered = false).signsWithoutAsking(), "its key is asked for at each load")
        assertFalse(AccountInfo(npub).signsWithoutAsking(), "read-only")
        assertFalse(AccountInfo(npub, loggedInWithExternalSigner = true).signsWithoutAsking(), "no signer apps on desktop")
        assertFalse(AccountInfo(npub, hasPrivKey = true, isTransient = true).signsWithoutAsking())
    }
}

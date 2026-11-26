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
package com.vitorpamplona.amethyst.commons.napplet.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class NappletStorageProfileTest {
    private val account = "bb".repeat(32)

    // `printf 'amethyst-webview-profile-v1:<bb x32>' | shasum -a 256`, the input Android's
    // NappletWebViewProfiles hashes: the names must stay equal, or an account loses its storage.
    @Test
    fun theNameIsAndroidsWebViewProfileName() {
        assertEquals("df2a8bad778f4e41a457f8eee1057756", NappletStorageProfile.forPubKey(account))
        assertEquals("df2a8bad-778f-4e41-a457-f8eee1057756", NappletStorageProfile.uuidFor(account))
    }

    @Test
    fun accountsGetTheirOwnJarAndNobodyGetsNone() {
        assertNotEquals(NappletStorageProfile.forPubKey(account), NappletStorageProfile.forPubKey("cc".repeat(32)))
        assertNull(NappletStorageProfile.forPubKey(""))
        assertNull(NappletStorageProfile.uuidFor(" "))
    }
}

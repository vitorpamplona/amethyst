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
package com.vitorpamplona.quartz.nip29RelayGroups

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * NIP-29 group identifier: the optional `?invite=<code>` suffix appended to a
 * `kind:39000` `naddr` (extracted from the trailing chars a NIP-19 parser returns
 * after the bech32 body).
 */
class GroupNAddrInviteTest {
    @Test
    fun parsesInviteParam() {
        assertEquals("abc123", GroupNAddrInvite.parse("?invite=abc123"))
    }

    @Test
    fun parsesInviteAmongOtherParams() {
        assertEquals("abc123", GroupNAddrInvite.parse("?foo=bar&invite=abc123"))
        assertEquals("abc123", GroupNAddrInvite.parse("?invite=abc123&foo=bar"))
    }

    @Test
    fun nullWhenNoSuffix() {
        assertNull(GroupNAddrInvite.parse(null))
        assertNull(GroupNAddrInvite.parse(""))
        assertNull(GroupNAddrInvite.parse("?"))
        assertNull(GroupNAddrInvite.parse("?other=1"))
    }

    @Test
    fun nullWhenInviteEmpty() {
        assertNull(GroupNAddrInvite.parse("?invite="))
    }

    @Test
    fun acceptsBareInviteWithoutQuestionMark() {
        // Defensive: if an upstream parser drops the `?`, a bare `invite=<code>` still resolves.
        assertEquals("abc123", GroupNAddrInvite.parse("invite=abc123"))
    }

    @Test
    fun ignoresLeadingBech32RemainderWithoutQuery() {
        // A plain trailing word (not a query) carries no invite.
        assertNull(GroupNAddrInvite.parse("someword"))
    }
}

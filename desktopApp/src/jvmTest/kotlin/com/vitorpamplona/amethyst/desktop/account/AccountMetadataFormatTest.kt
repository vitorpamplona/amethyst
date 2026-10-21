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
package com.vitorpamplona.amethyst.desktop.account

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The accounts file must keep its format across the move from Jackson to kotlinx: these strings
 * are exactly what Jackson's `jacksonObjectMapper()` wrote for the same values.
 */
class AccountMetadataFormatTest {
    private val full =
        AccountMetadata(
            accounts =
                listOf(
                    AccountInfoDto("npub1a", "internal"),
                    AccountInfoDto("npub1b", "remote", "bunker://x?relay=wss://r", "Bob \"B\"", true, "abc", "wss://nwc"),
                ),
            activeNpub = "npub1a",
        )

    private val writtenByJackson =
        """{"accounts":[{"npub":"npub1a","signerKind":"internal","bunkerUri":null,"displayName":null,"isTransient":false,"nwcPubKey":null,"nwcRelay":null},""" +
            """{"npub":"npub1b","signerKind":"remote","bunkerUri":"bunker://x?relay=wss://r","displayName":"Bob \"B\"","isTransient":true,"nwcPubKey":"abc","nwcRelay":"wss://nwc"}],"activeNpub":"npub1a"}"""

    @Test
    fun writesTheBytesJacksonWrote() {
        assertEquals(writtenByJackson, full.encode().decodeToString())
        assertEquals("""{"accounts":[],"activeNpub":null}""", AccountMetadata().encode().decodeToString())
    }

    @Test
    fun readsWhatJacksonWrote() {
        assertEquals(full, AccountMetadata.decode(writtenByJackson.encodeToByteArray()))
        assertEquals(AccountMetadata(), AccountMetadata.decode("""{"accounts":[],"activeNpub":null}""".encodeToByteArray()))
    }

    @Test
    fun toleratesMissingUnknownAndNullFields() {
        val read =
            AccountMetadata.decode(
                """{"accounts":[{"npub":"npub1c","signerKind":"viewonly","isTransient":null,"addedLater":{"x":1}}],"futureField":true}""".encodeToByteArray(),
            )
        assertEquals(AccountMetadata(listOf(AccountInfoDto("npub1c", "viewonly"))), read)
    }
}

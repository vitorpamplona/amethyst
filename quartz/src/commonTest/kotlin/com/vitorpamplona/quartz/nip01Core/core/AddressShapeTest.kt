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
package com.vitorpamplona.quartz.nip01Core.core

import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyAddressTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootAddressTag
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Address hint parsers used to accept anything with a `:`, so `["A","foo:bar","wss://r"]` put a
 * fake address into the hint index. [AddressSerializer.isAddressShape] is the cheap gate.
 */
class AddressShapeTest {
    private val pk = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val relay = "wss://relay.damus.io"

    private val valid = listOf("30023:$pk:article", "30023:$pk:", "0:$pk:x:y", "10002:${pk.uppercase()}:", "1:$pk:my.article")

    private val invalid =
        listOf(
            "",
            "foo:bar",
            ":$pk:x",
            "300230:$pk:x",
            "a30023:$pk:x",
            "30023:$pk",
            "30023:${pk.dropLast(1)}:x",
            "30023:${pk.dropLast(1)}z:x",
            "30023:${pk.dropLast(1)}中:x",
            "30023:$pk" + "0:x",
            "naddr1qqxnzd3cxqmrzv3exgmr2wfeqgsxu35yyt0mwjjh8pcz4zprhxegz69t4wr9t74vk6zne58wzh0waycrqsqqqa28pccpzu",
        )

    @Test
    fun shape() {
        valid.forEach { assertTrue(AddressSerializer.isAddressShape(it), it) }
        invalid.forEach { assertFalse(AddressSerializer.isAddressShape(it), it) }
    }

    @Test
    fun addressHintParsersRejectWhatIsNotAnAddress() {
        invalid.forEach {
            assertNull(ATag.parseAsHint(arrayOf("a", it, relay)), it)
            assertNull(RootAddressTag.parseAsHint(arrayOf("A", it, relay)), it)
            assertNull(ReplyAddressTag.parseAsHint(arrayOf("a", it, relay)), it)
        }
        valid.forEach {
            assertNotNull(ATag.parseAsHint(arrayOf("a", it, relay)), it)
            assertNotNull(RootAddressTag.parseAsHint(arrayOf("A", it, relay)), it)
            assertNotNull(ReplyAddressTag.parseAsHint(arrayOf("a", it, relay)), it)
        }
    }
}

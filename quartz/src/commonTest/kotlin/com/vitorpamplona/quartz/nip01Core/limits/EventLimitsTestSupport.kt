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
package com.vitorpamplona.quartz.nip01Core.limits

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal fun assertRejectsBecause(
    parse: (String) -> Event,
    json: String,
    expectedMessageFragment: String,
) {
    val ex = assertFailsWith<IllegalArgumentException> { parse(json) }
    assertEquals(true, ex.message?.contains(expectedMessageFragment), ex.message)
}

internal object EventLimitsTestSupport {
    const val ID = "0000000000000000000000000000000000000000000000000000000000000001"
    const val PUB_KEY = "0000000000000000000000000000000000000000000000000000000000000002"
    val SIG = "0".repeat(128)

    fun buildEventJson(
        id: String = ID,
        pubKey: String = PUB_KEY,
        sig: String = SIG,
        kind: Int = 1,
        createdAt: Long = TimeUtils.now(),
        contentLength: Int = 10,
        tagCount: Int = 1,
        tagInnerCount: Int = 2,
        tagValueLength: Int = 5,
    ): String {
        val content = "x".repeat(contentLength)
        val tagValue = "v".repeat(tagValueLength)
        // First element is a short tag key "t"; remaining (tagInnerCount - 1) are values of length tagValueLength.
        val inner = "\"t\"" + ",\"$tagValue\"".repeat((tagInnerCount - 1).coerceAtLeast(0))
        val tag = "[$inner]"
        val tags = (1..tagCount).joinToString(",") { tag }
        return """{"id":"$id","pubkey":"$pubKey","created_at":$createdAt,"kind":$kind,"tags":[$tags],"content":"$content","sig":"$sig"}"""
    }
}

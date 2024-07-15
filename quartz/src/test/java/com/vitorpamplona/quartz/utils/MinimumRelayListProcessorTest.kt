/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.utils

import junit.framework.TestCase.assertEquals
import org.junit.Test

class MinimumRelayListProcessorTest {
    val userList =
        mutableMapOf(
            "User1" to mutableSetOf("wss://relay1.com", "wss://relay2.com", "wss://relay3.com"),
            "User2" to mutableSetOf("wss://relay4.com", "wss://relay5.com", "wss://relay6.com"),
            "User3" to mutableSetOf("wss://relay1.com", "wss://relay4.com", "wss://relay6.com"),
            "User4" to mutableSetOf("wss://relay2.com", "wss://relay1.com", "wss://relay4.com"),
        )

    @Test
    fun testTranspose() {
        assertEquals(
            mapOf(
                "wss://relay1.com" to listOf("User1", "User3", "User4"),
                "wss://relay2.com" to listOf("User1", "User4"),
                "wss://relay3.com" to listOf("User1"),
                "wss://relay4.com" to listOf("User2", "User3", "User4"),
                "wss://relay5.com" to listOf("User2"),
                "wss://relay6.com" to listOf("User2", "User3"),
            ).toString(),
            MinimumRelayListProcessor.transpose(userList).toString(),
        )
    }

    @Test
    fun test1() {
        assertEquals(
            listOf("wss://relay1.com", "wss://relay4.com", "wss://relay2.com", "wss://relay5.com").toString(),
            MinimumRelayListProcessor.reliableRelaySetFor(userList).toString(),
        )
    }
}

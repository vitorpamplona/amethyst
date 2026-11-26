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
package com.vitorpamplona.amethyst.commons.devices.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class DeviceChatFocusTest {
    private fun chat(key: String) = ChatFocus(key, key, null, File("/tmp/$key"), "Claude") {}

    @Test
    fun whenTheLatestChatLeavesTheOneStillShownIsFollowedAgain() {
        val a = chat("a")
        val b = chat("b")
        val leaveA = DeviceChatFocus.publish(a)
        val leaveB = DeviceChatFocus.publish(b)
        assertSame(b, DeviceChatFocus.current)

        leaveB()
        assertSame(a, DeviceChatFocus.current, "a deck column still shows chat a")

        // Out of order: the one in front stays.
        val leaveB2 = DeviceChatFocus.publish(b)
        leaveA()
        assertSame(b, DeviceChatFocus.current)
        // The last chat leaving (a tab switch) keeps the panel on it: its device stays connected.
        leaveB2()
        assertSame(b, DeviceChatFocus.current)
        DeviceChatFocus.forget("b")
        assertNull(DeviceChatFocus.current)
    }
}

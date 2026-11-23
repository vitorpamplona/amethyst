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
package com.vitorpamplona.amethyst.commons.model.cache

import com.vitorpamplona.quartz.concord.cord05Invites.bundle.ConcordInviteBundleEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A Concord invite bundle (kind 33301) is fetched on purpose, by the invite preview and by a link
 * refresh, so every one that arrives reached EventCache's dispatch and fell through to the
 * "Event Not Supported" warning. It is a public addressable event the note card renders
 * (RenderConcordInviteBundle), so it is stored at its address like any other.
 */
class ConcordInviteBundleConsumeTest {
    // Published by a local Concord invite during a desktop run (loopback relay).
    private val bundle =
        """
        {"id":"b822d64f5933c0d6819df2a0a281ed659bd72d76812209fd5c0035c18d3d3a4f","pubkey":"77f2b60642722a408abbf09365ea0f6eb5fa42b22d0d60197e14d23401eb17b4","created_at":1795358653,"kind":33301,"tags":[["d",""],["vsk","6"]],"content":"AvrjflYK1Lxz5WLaMoiypFfZjzlQJkfjz88hCNQENwkR11nGnbom0uDVw+y5V6lDTosWNeqtP71QvPVBeewpVk+yinQXy20VAFiLYnsQ70WNlwhgOZFKztVOTmCMGYGs+49VMhV3uNMXlrYYzAGkoWy9VDVbefLKY8xcJDYP/aqfbZzQ+6tL0CDQ+uC6idwFFhvHYRVv1NaCU7Y9rJoax8zczfWxtyllPWP8OkNpqx+8H8mWWK8RuR5zexn4sX/3HBcpyd1LhNLKxva9HoD6O9TEWPdBpl/+iYF4YXM8OtXmxJwaxBxNIfw/v8+hw8yLif2GAIJjJsK043aZCnJ3uB37Q4FlpTslqOfjMQ2zyuBvL/OKsKDAy3sd1y37n4ys7E4g5cGHxHr48pj458dRBKiRo6h/wu+6RC3z5BKNVa7+Wbe2HrCQao/CeVliwGIehz+wjLs/QzPQcRZCgyKGo+LGO9pyZoEPUIQkQxRyZt2GffOD3zXYCUpS1taGmerPAnzhgxyHT8zzz0ZHjj53GD2VpQJGIZe4ZCz5ndd52LRHUz71XbnYa1pH60P6AM+DEjSf+fTNYOK5B+0fndliHgKT5isiD6B+ZesxRZ3xcOAGejCzKKkeMbMSKNtNIbKcLp6OxPEGLU7UZ/X5GO0kEHFbI6pxIOcCncNQU16r0b1TIEeXoa1ttGnOZAoYA+ivpy1jkHa4sE0x/q6Va7RV1x9EEHKWTqQLdyh6noWUQWp+q9f+ssX1QODAI7BPncX150+S4pqhxbIaZ1+t2R3ESapYv7jbgDJHaK7iaXQlFFh8PBhmq2vWReuDH/zSLA5Wq5QwgplHEwvCmyOP202mFjZxJbVMeXiLhgLqsm5/zBSXzmuquO5xvzLiZmG6ayiTc25O8pdSRSvGfstyu5C5F3eounHKyG8MT5ej+k9FRxoGzHw=","sig":"e5cf3cc9fa862320da77c003004caf983f62c9af30952fbacf8c8f5522df429e2e20ff981529f3877aa9cc05621ce41b4354cd54da0c2161fccac33f64a827d4"}
        """.trimIndent()

    @Test
    fun consumesAnInviteBundleAtItsAddress() {
        val event = Event.fromJson(bundle)
        assertIs<ConcordInviteBundleEvent>(event)

        val cache = EventCache()
        assertTrue(cache.justConsume(event, null, true))

        val note = cache.getAddressableNoteIfExists(event.address())
        assertSame(event, note?.event)
    }
}

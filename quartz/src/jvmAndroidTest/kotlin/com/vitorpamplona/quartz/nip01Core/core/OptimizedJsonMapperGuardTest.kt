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

import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [OptimizedJsonMapper.toJson] refuses a type that neither [NostrJsonWriter] nor a kotlinx
 * serializer covers, on the first call, rather than writing something on a user's device.
 */
class OptimizedJsonMapperGuardTest {
    private class NotRegistered(
        val someField: String = "value",
    ) : OptimizedSerializable

    @Test
    fun unregisteredTypeIsRefused() {
        val e = assertFailsWith<IllegalArgumentException> { OptimizedJsonMapper.toJson(NotRegistered()) }
        assertTrue(e.message!!.contains("Unsupported type"), e.message!!)
    }

    /** Subclasses of a covered root, like CLOSE under Command, are covered too. */
    @Test
    fun subclassesOfARegisteredRootStillSerialize() {
        assertEquals("""["CLOSE","sub-1"]""", OptimizedJsonMapper.toJson(CloseCmd("sub-1")))
    }

    @Test
    fun registeredRootsStillSerialize() {
        assertEquals("""{"kinds":[1]}""", OptimizedJsonMapper.toJson(Filter(kinds = listOf(1))))
        assertTrue(OptimizedJsonMapper.toJson(BunkerRequest("id-1", "connect", arrayOf("a"))).contains("\"connect\""))
    }
}

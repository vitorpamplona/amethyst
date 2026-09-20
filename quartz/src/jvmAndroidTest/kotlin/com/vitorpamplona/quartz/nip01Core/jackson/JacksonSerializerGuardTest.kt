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
package com.vitorpamplona.quartz.nip01Core.jackson

import com.vitorpamplona.quartz.nip01Core.core.OptimizedSerializable
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [JacksonMapper.toJson] refuses a type it has no registered serializer for.
 *
 * Bean introspection would otherwise name every field after its getter, and R8
 * renames getters — so the JSON is correct in debug and one-letter garbage in a
 * release build. The failure has to happen here, on the first call, or it happens
 * on a user's device.
 */
class JacksonSerializerGuardTest {
    private class NotRegistered(
        val someField: String = "value",
    ) : OptimizedSerializable

    @Test
    fun unregisteredTypeIsRefused() {
        val e = assertFailsWith<IllegalArgumentException> { JacksonMapper.toJson(NotRegistered()) }
        assertTrue(e.message!!.contains("No Jackson serializer is registered"), e.message!!)
    }

    /**
     * The guard is an `is` chain because Jackson's SimpleSerializers walks the
     * hierarchy: CLOSE has no serializer of its own, it rides Command's. An
     * exact-class check would reject it — and with it every relay command the
     * client sends.
     */
    @Test
    fun subclassesOfARegisteredRootStillSerialize() {
        assertEquals("""["CLOSE","sub-1"]""", JacksonMapper.toJson(CloseCmd("sub-1")))
    }

    @Test
    fun registeredRootsStillSerialize() {
        assertEquals("""{"kinds":[1]}""", JacksonMapper.toJson(Filter(kinds = listOf(1))))
        assertTrue(JacksonMapper.toJson(BunkerRequest("id-1", "connect", arrayOf("a"))).contains("\"connect\""))
    }
}

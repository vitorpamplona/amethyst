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
package com.vitorpamplona.amethyst.commons.model.marmot

import com.vitorpamplona.amethyst.commons.model.marmot.devices.DeviceGroupBlobs
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotChatSetting
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotChatSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarmotChatSettingsTest {
    private class MemoryBlobs : DeviceGroupBlobs {
        val saved = HashMap<String, ByteArray>()

        override suspend fun load(name: String) = saved[name]

        override suspend fun save(
            name: String,
            bytes: ByteArray,
        ) {
            saved[name] = bytes
        }
    }

    private val a = "aa".repeat(32)
    private val b = "bb".repeat(32)

    @Test
    fun settingsSurviveARestartAndMuteTimesPass() =
        runBlocking {
            val blobs = MemoryBlobs()
            val first = MarmotChatSettings(blobs)
            first.load()
            assertTrue(first.set(a, MarmotChatSetting.PIN, true))
            assertFalse("the same value changes nothing", first.set(a, MarmotChatSetting.PIN, true))
            first.set(b, MarmotChatSetting.ARCHIVE, true)
            // A linked device muted b until a time already past: not muted here.
            first.applyRemote(b, MarmotChatSetting.MUTE, "1000")
            first.applyRemote(a, MarmotChatSetting.MUTE, "true")

            val second = MarmotChatSettings(blobs)
            second.load()
            val s = second.state.value
            assertTrue(s.isPinned(a))
            assertTrue(s.isArchived(b))
            assertTrue(s.isMuted(a))
            assertFalse(s.isMuted(b, now = 2000))
            assertEquals(
                setOf(Triple(a, MarmotChatSetting.PIN, "true"), Triple(a, MarmotChatSetting.MUTE, "true"), Triple(b, MarmotChatSetting.ARCHIVE, "true")),
                s.nonDefault(now = 2000).toSet(),
            )

            second.forget(a)
            assertFalse(second.state.value.isPinned(a))
        }
}

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
package com.vitorpamplona.quartz.nipACWebRtcCalls

import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallIdTag
import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType
import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallTypeTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CallTagsTest {
    @Test
    fun parseCallIdTag() {
        val tag = arrayOf("call-id", "550e8400-e29b-41d4-a716-446655440000")
        val result = CallIdTag.parse(tag)
        assertNotNull(result)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", result)
    }

    @Test
    fun parseCallIdTagRejectsEmpty() {
        val tag = arrayOf("call-id", "")
        assertNull(CallIdTag.parse(tag))
    }

    @Test
    fun parseCallIdTagRejectsMissingValue() {
        val tag = arrayOf("call-id")
        assertNull(CallIdTag.parse(tag))
    }

    @Test
    fun parseCallIdTagRejectsWrongName() {
        val tag = arrayOf("other", "some-id")
        assertNull(CallIdTag.parse(tag))
    }

    @Test
    fun assembleCallIdTag() {
        val tag = CallIdTag.assemble("test-id-123")
        assertEquals("call-id", tag[0])
        assertEquals("test-id-123", tag[1])
    }

    @Test
    fun roundTripCallIdTag() {
        val original = "550e8400-e29b-41d4-a716-446655440000"
        val assembled = CallIdTag.assemble(original)
        val parsed = CallIdTag.parse(assembled)
        assertEquals(original, parsed)
    }

    @Test
    fun parseCallTypeVoice() {
        val tag = arrayOf("call-type", "voice")
        val result = CallTypeTag.parse(tag)
        assertNotNull(result)
        assertEquals(CallType.VOICE, result)
    }

    @Test
    fun parseCallTypeVideo() {
        val tag = arrayOf("call-type", "video")
        val result = CallTypeTag.parse(tag)
        assertNotNull(result)
        assertEquals(CallType.VIDEO, result)
    }

    @Test
    fun parseCallTypeRejectsUnknown() {
        val tag = arrayOf("call-type", "screenshare")
        assertNull(CallTypeTag.parse(tag))
    }

    @Test
    fun parseCallTypeRejectsMissing() {
        val tag = arrayOf("call-type")
        assertNull(CallTypeTag.parse(tag))
    }

    @Test
    fun assembleCallTypeVoice() {
        val tag = CallTypeTag.assemble(CallType.VOICE)
        assertEquals("call-type", tag[0])
        assertEquals("voice", tag[1])
    }

    @Test
    fun assembleCallTypeVideo() {
        val tag = CallTypeTag.assemble(CallType.VIDEO)
        assertEquals("call-type", tag[0])
        assertEquals("video", tag[1])
    }

    @Test
    fun roundTripCallType() {
        for (type in CallType.entries) {
            val assembled = CallTypeTag.assemble(type)
            val parsed = CallTypeTag.parse(assembled)
            assertEquals(type, parsed)
        }
    }

    @Test
    fun callTypeFromString() {
        assertEquals(CallType.VOICE, CallType.fromString("voice"))
        assertEquals(CallType.VIDEO, CallType.fromString("video"))
        assertNull(CallType.fromString("audio"))
        assertNull(CallType.fromString(""))
    }
}

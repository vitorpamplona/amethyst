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
package com.vitorpamplona.emulator

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmulatorMessagesTest {
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    @Test
    fun imageFormatRequestUsesTheProtoFieldNumbers() {
        // format = 1 (RGBA8888), width = 3, height = 4, display = 5
        assertEquals("0801189003208007", hex(EmulatorProto.imageFormat(ImageEncoding.RGBA8888, 400, 896, 0)))
        assertEquals("08022801", hex(EmulatorProto.imageFormat(ImageEncoding.RGB888, 0, 0, 1)))
    }

    @Test
    fun imageRoundTripsWithRotationAndSequence() {
        val pixels = ByteArray(2 * 3 * 4) { it.toByte() }
        val frame =
            EmulatorFrame(
                width = 2,
                height = 3,
                encoding = ImageEncoding.RGBA8888,
                pixels = pixels,
                seq = 7,
                timestampUs = 1_700_000_000_123_456,
                rotation = SkinRotation.REVERSE_LANDSCAPE,
                display = 0,
            )
        val decoded = EmulatorProto.decodeImage(EmulatorProto.encodeImage(frame))
        assertEquals(2, decoded.width)
        assertEquals(3, decoded.height)
        assertEquals(ImageEncoding.RGBA8888, decoded.encoding)
        assertContentEquals(pixels, decoded.pixels)
        assertEquals(7, decoded.seq)
        assertEquals(1_700_000_000_123_456, decoded.timestampUs)
        assertEquals(SkinRotation.REVERSE_LANDSCAPE, decoded.rotation)
    }

    @Test
    fun imageFallsBackToTheDeprecatedWidthAndHeight() {
        // Image { width = 2: 10, height = 3: 20, image = 4: [1] }, no format.
        val bytes = byteArrayOf(0x10, 10, 0x18, 20, 0x22, 1, 1)
        val frame = EmulatorProto.decodeImage(bytes)
        assertEquals(10, frame.width)
        assertEquals(20, frame.height)
        assertEquals(ImageEncoding.PNG, frame.encoding)
        assertEquals(SkinRotation.PORTRAIT, frame.rotation)
    }

    @Test
    fun statusRoundTrips() {
        val status =
            EmulatorStatus(
                version = "35.1.4.0",
                uptimeMs = 123_456,
                booted = true,
                vmConfig = VmConfiguration(Hypervisor.KVM, 4, 2L * 1024 * 1024 * 1024),
                hardwareConfig = linkedMapOf("hw.lcd.width" to "1080", "hw.lcd.height" to "2400", "hw.lcd.density" to "420"),
                heartbeat = 99,
                guestConfig = linkedMapOf("ro.build.version.sdk" to "35"),
            )
        val decoded = EmulatorProto.decodeStatus(EmulatorProto.encodeStatus(status))
        assertEquals(status, decoded)
        assertEquals(1080, decoded.displayWidth)
        assertEquals(2400, decoded.displayHeight)
    }

    @Test
    fun touchEventEncoding() {
        val bytes =
            EmulatorProto.touchEvent(
                listOf(Touch(x = 10, y = 20, identifier = 1, pressure = 5), Touch(x = 10, y = 20, identifier = 1, pressure = 0)),
                display = 0,
            )
        // touches = 1: { x = 1: 10, y = 2: 20, identifier = 3: 1, pressure = 4: 5 }, then the release.
        assertEquals("0a08080a1014180120050a06080a10141801", hex(bytes))
    }

    @Test
    fun keyboardEventEncoding() {
        // eventType = 2: keypress (2), key = 4: "GoBack"
        assertEquals("1002220647" + "6f4261636b", hex(EmulatorProto.keyboardEvent(KeyboardEvent.press("GoBack"))))
        // text = 5 only; eventType keypress is still sent, which the emulator ignores with text.
        assertEquals("10022a026869", hex(EmulatorProto.keyboardEvent(KeyboardEvent.type("hi"))))
        // keydown is the proto3 default (0) and so is not on the wire.
        assertEquals("2205456e746572", hex(EmulatorProto.keyboardEvent(KeyboardEvent(KeyEventType.KEY_DOWN, key = "Enter"))))
    }

    @Test
    fun mouseAndWheelEncoding() {
        assertEquals("0864106418012001", hex(EmulatorProto.mouseEvent(100, 100, 1, 1)))
        // dy = -120 sign-extends to ten bytes.
        assertEquals("1088ffffffffffffffff01", hex(EmulatorProto.wheelEvent(0, -120, 0)))
    }

    @Test
    fun physicalModelRotationRoundTrips() {
        val bytes = EmulatorProto.physicalModel(EmulatorProto.PHYSICAL_TYPE_ROTATION, listOf(0f, 0f, -90f))
        // target = 1: ROTATION (1), value = 3: { data = 1: packed 3 floats (12 bytes) }
        assertTrue(hex(bytes).startsWith("08011a0e0a0c"))
        assertEquals(listOf(0f, 0f, -90f), EmulatorProto.decodePhysicalModelValues(bytes))
    }

    @Test
    fun vmRunStateRoundTrips() {
        VmRunState.entries.forEach { assertEquals(it, EmulatorProto.decodeVmRunState(EmulatorProto.vmRunState(it))) }
        assertEquals("0803", hex(EmulatorProto.vmRunState(VmRunState.PAUSED)))
    }
}

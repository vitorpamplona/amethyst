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
package com.vitorpamplona.quic.http3

import com.vitorpamplona.quic.QuicCodecException
import com.vitorpamplona.quic.QuicWriter
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * RFC 9114 §7.2.4.1 — SETTINGS identifiers in the HTTP/2 range
 * (0x02, 0x03, 0x04, 0x05) are reserved to prevent confusion with
 * HTTP/2 settings. Receipt of any of these MUST be a connection error
 * of type H3_SETTINGS_ERROR.
 *
 * Pre-fix the decoder validated values per known id but accepted the
 * reserved ids silently (since they weren't in the known cap table —
 * they fell through to the generic 1<<32 cap).
 */
class Http3ReservedSettingsTest {
    private fun encodeRawSetting(
        id: Long,
        value: Long,
    ): ByteArray {
        val w = QuicWriter()
        w.writeVarint(id)
        w.writeVarint(value)
        return w.toByteArray()
    }

    @Test
    fun reserved_id_0x02_rejected() {
        val ex =
            assertFailsWith<QuicCodecException> {
                Http3Settings.decodeBody(encodeRawSetting(0x02L, 1L))
            }
        assertTrue(ex.message!!.contains("H3_SETTINGS_ERROR"))
    }

    @Test
    fun reserved_id_0x03_rejected() {
        assertFailsWith<QuicCodecException> {
            Http3Settings.decodeBody(encodeRawSetting(0x03L, 1L))
        }
    }

    @Test
    fun reserved_id_0x04_rejected() {
        assertFailsWith<QuicCodecException> {
            Http3Settings.decodeBody(encodeRawSetting(0x04L, 1L))
        }
    }

    @Test
    fun reserved_id_0x05_rejected() {
        assertFailsWith<QuicCodecException> {
            Http3Settings.decodeBody(encodeRawSetting(0x05L, 1L))
        }
    }

    @Test
    fun adjacent_legal_ids_still_accepted() {
        // 0x01 is RFC 9204 QPACK_MAX_TABLE_CAPACITY, 0x06 is the next
        // legal HTTP/3 setting. Confirm the reserved-range check does
        // not over-reach.
        Http3Settings.decodeBody(encodeRawSetting(0x01L, 4096L))
        Http3Settings.decodeBody(encodeRawSetting(0x06L, 1024L))
    }
}

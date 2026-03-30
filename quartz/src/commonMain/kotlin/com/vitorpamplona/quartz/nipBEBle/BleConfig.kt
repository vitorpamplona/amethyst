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
package com.vitorpamplona.quartz.nipBEBle

/**
 * NIP-BE constants for BLE Nostr communications.
 */
object BleConfig {
    /** Service UUID for device advertisement. */
    const val SERVICE_UUID = "0000180f-0000-1000-8000-00805f9b34fb"

    /** Write characteristic UUID (client writes commands to server). */
    const val WRITE_CHARACTERISTIC_UUID = "87654321-0000-1000-8000-00805f9b34fb"

    /** Read characteristic UUID (server notifies/sends messages to client). */
    const val READ_CHARACTERISTIC_UUID = "12345678-0000-1000-8000-00805f9b34fb"

    /** Device UUID for a permanent GATT Server role. */
    const val PERMANENT_SERVER_UUID = "FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF"

    /** Device UUID for a permanent GATT Client role. */
    const val PERMANENT_CLIENT_UUID = "00000000-0000-0000-0000-000000000000"

    /** Maximum compressed message size (64KB). */
    const val MAX_MESSAGE_SIZE = 64 * 1024

    /**
     * Default payload size per chunk in bytes (excluding 2-byte overhead).
     * Each transmitted chunk is `DEFAULT_CHUNK_SIZE + 2` bytes on the wire.
     */
    const val DEFAULT_CHUNK_SIZE = 500

    /** Default MTU to request during BLE connection. */
    const val DEFAULT_MTU = 512

    /** Minimum MTU for BLE 4.2. */
    const val MIN_MTU_BLE_4_2 = 20

    /** Default MTU for BLE 5.x. */
    const val DEFAULT_MTU_BLE_5 = 256
}

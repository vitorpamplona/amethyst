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
 * BLE role assignment per NIP-BE.
 *
 * The device with the highest UUID becomes the GATT Server (Relay),
 * the other becomes the GATT Client (Client).
 */
enum class BleRole {
    /** GATT Server role - acts as a Nostr relay. */
    SERVER,

    /** GATT Client role - acts as a Nostr client. */
    CLIENT,
}

/**
 * Determines the role for a device when it discovers a peer.
 *
 * Per NIP-BE: the device with the highest UUID takes the SERVER (Relay) role,
 * the other takes the CLIENT role and initiates the connection.
 *
 * @param myUuid This device's UUID as a hex string (uppercase, with dashes).
 * @param peerUuid The discovered peer's UUID as a hex string.
 * @return The role this device should assume.
 */
fun assignRole(
    myUuid: String,
    peerUuid: String,
): BleRole {
    val comparison = myUuid.uppercase().compareTo(peerUuid.uppercase())
    return if (comparison > 0) BleRole.SERVER else BleRole.CLIENT
}

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
package com.vitorpamplona.quartz.marmot.mip05PushNotifications

/**
 * The platform a push token belongs to (`features/push-notifications.md`).
 *
 * Two encodings for the same thing, and both are load-bearing: [wireName] is
 * what a gossip entry's `platform` member and the owner-proof tag carry, while
 * [byte] is what goes into the encrypted token plaintext, the fingerprint
 * preimage and the canonical [PushSignedRecord]. Keeping them on one type is
 * what stops a signer and a verifier from disagreeing about which is which.
 */
enum class PushPlatform(
    val wireName: String,
    val byte: Byte,
) {
    APNS("apns", 0x01),
    FCM("fcm", 0x02),
    ;

    companion object {
        /** Null for an unknown platform — the entry is then advisory-invalid, not an error. */
        fun fromWireName(name: String): PushPlatform? = entries.firstOrNull { it.wireName == name }

        fun fromByte(value: Byte): PushPlatform? = entries.firstOrNull { it.byte == value }
    }
}

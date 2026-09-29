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
package com.vitorpamplona.quartz.nipXXPushNotifications.preferences

import kotlinx.serialization.Serializable

/**
 * The decrypted payload of a [PushPreferencesEvent].
 *
 * [kinds] lists notification *categories*, which the service defines. They are numbered after event
 * kinds but need not match what triggers them: divine-push-service reads `1` as "comments and
 * mentions" (sent for kinds 1111, 30023 and 34236), `3` follows, `7` likes, `16` reposts, and
 * `34236` new posts from authors the user subscribed to. An empty list turns everything off.
 *
 * [campaignsEnabled] is a separate opt-in for engagement campaigns; no category implies it.
 */
@Serializable
data class PushPreferences(
    val kinds: List<Int>,
    val campaignsEnabled: Boolean = false,
)

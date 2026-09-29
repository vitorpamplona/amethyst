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
package com.vitorpamplona.quartz.experimental.divine

/**
 * Kinds divine.video (<https://github.com/divinevideo>) publishes for its own infrastructure.
 *
 * None of them carry content a client renders, so Quartz has no event classes for them: they are
 * registered only so relay statistics and `amy kind` can put a name on them instead of a number.
 */
object DivineKinds {
    /**
     * Registers a device's push token with divine-push-service. The content is NIP-44 encrypted to
     * the service's `p`-tagged key. Spec: divine-push-service `docs/nip-xx-push-notifications.md`.
     */
    const val PUSH_REGISTRATION = 3079

    /** Removes a token registered with [PUSH_REGISTRATION]. Same draft, same encrypted shape. */
    const val PUSH_DEREGISTRATION = 3080

    /** Which notification categories the push service should send, NIP-44 encrypted to it. */
    const val PUSH_PREFERENCES = 3083

    /**
     * Ephemeral "watched this video" analytics, `a`/`e`-tagging a kind-34236 video with `phase`,
     * `viewed`, `loops` and `source` tags. Divine's relay turns these into view and loop counts.
     */
    const val VIDEO_VIEW = 22236
}

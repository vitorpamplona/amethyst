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
package com.vitorpamplona.amethyst.service.playback

/**
 * TEMPORARY — delete this file and its three call sites before merging.
 * `grep -rn HlsVerify` finds all of them; reverting the commit that added it does the same job.
 *
 * [PLAYBACK_DIAG_TAG] logs at DEBUG, and `Amethyst.onCreate` sets
 * `Log.minLevel = if (BuildConfig.DEBUG) DEBUG else ERROR` — so nothing on that tag survives in a
 * benchmark or release build. On-device verification of the HLS work therefore has to log at ERROR
 * to be visible at all.
 *
 * Deliberately placed only on the paths that unit tests *cannot* reach:
 * - `isHlsMediaItem` and the factory choice — needs `android.net.Uri`, which stubs to null under
 *   `unitTests.isReturnDefaultValues`.
 * - `LowLatencyStrippingParser.parse` — needs a `Uri` for the same reason.
 * - `HlsLivenessRecorder.maybeRecord` — needs a `Player`.
 *
 * The pure predicates underneath (`stripLowLatencyTags`, `isHlsMedia`, `shouldBypassCache`,
 * `livenessVerdictToRecord`) already have unit tests, which are the durable regression guard. This
 * tag is only for confirming the wiring on a real device.
 *
 * Capture with: `adb logcat -s HlsVerify:E`
 */
const val HLS_VERIFY_TAG = "HlsVerify"

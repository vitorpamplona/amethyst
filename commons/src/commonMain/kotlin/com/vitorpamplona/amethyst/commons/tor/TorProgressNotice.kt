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
package com.vitorpamplona.amethyst.commons.tor

/**
 * What the Tor progress notification should show. Pure, so the mapping is unit-testable, and
 * shared so any front end with a progress surface maps Tor's state the same way (Android's
 * opt-in `TorProgressNotifier` today).
 */
sealed class TorProgressNotice {
    /** Opted out, or Tor is off: no notification. */
    data object Hidden : TorProgressNotice()

    /** The native client is being created; no proxy yet. */
    data object Starting : TorProgressNotice()

    /** The proxy is up and the directory is downloading. [percent] is null before Arti reports any. */
    data class Downloading(
        val percent: Int?,
    ) : TorProgressNotice()

    /** Fully bootstrapped. */
    data object Ready : TorProgressNotice()

    val isInProgress: Boolean get() = this is Starting || this is Downloading

    companion object {
        /**
         * @param permille Arti's directory-download progress, 0-1000, or negative when unknown
         *   (see [TorBackend.bootstrapProgress]).
         */
        fun from(
            enabled: Boolean,
            status: TorServiceStatus,
            permille: Int,
        ): TorProgressNotice =
            if (!enabled) {
                Hidden
            } else {
                when (status) {
                    TorServiceStatus.Off -> Hidden
                    TorServiceStatus.Connecting -> Starting
                    is TorServiceStatus.Bootstrapping -> Downloading(if (permille >= 0) (permille / 10).coerceIn(0, 100) else null)
                    is TorServiceStatus.Active -> Ready
                    // Only the desktop backend reports Error, and it has no progress surface.
                    is TorServiceStatus.Error -> Hidden
                }
            }
    }
}

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
package com.vitorpamplona.amethyst.commons.video.hls

/**
 * How a player whose engine fetches URLs with its own networking (iOS's AVFoundation, which takes
 * no proxy) gets a video's bytes, so none of them leaves outside Tor when the settings say Tor.
 */
enum class VideoRoute {
    /** The engine opens the URL itself: a local file, or a stream that need not go through Tor. */
    PLAIN,

    /** The app's disk cache serves the bytes, fetched over the video role's client. */
    CACHE,

    /** A loopback relay fetches every playlist, segment and key through Tor for the engine. */
    TOR_RELAY,

    /** It must go through Tor and nothing here can carry it (not http(s)): refused, never direct. */
    REFUSED,
    ;

    companion object {
        /**
         * The route of [url]: [viaCache] when the disk cache would take it, [mustUseTor] when the
         * Tor settings route it through Tor.
         */
        fun of(
            url: String,
            viaCache: Boolean,
            mustUseTor: Boolean,
        ): VideoRoute {
            val scheme = url.substringBefore(':', "").lowercase()
            // A file on the device never leaves it.
            if (scheme == "file") return PLAIN
            val http = scheme == "http" || scheme == "https"
            return when {
                http && viaCache -> CACHE
                !mustUseTor -> PLAIN
                http -> TOR_RELAY
                else -> REFUSED
            }
        }
    }
}

/** Why a request a loopback relay made for a player failed, for the player to say. */
sealed class RelayFailure {
    abstract val url: String

    /** Tor is required and not connected: nothing was sent. Worth a retry once Tor is up. */
    data class TorUnavailable(
        override val url: String,
    ) : RelayFailure()

    /** A playlist names a file the relay cannot fetch (not http(s)), so it was refused whole. */
    data class Unroutable(
        override val url: String,
    ) : RelayFailure()

    /** The server answered with an error status. */
    data class Http(
        override val url: String,
        val status: Int,
    ) : RelayFailure()

    /** The connection failed, or the body was not what it claimed (a playlist too big). */
    data class Network(
        override val url: String,
        val message: String,
    ) : RelayFailure()
}

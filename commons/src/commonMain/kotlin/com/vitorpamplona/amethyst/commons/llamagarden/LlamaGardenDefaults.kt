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
package com.vitorpamplona.amethyst.commons.llamagarden

import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Amethyst's choices for llama.garden (https://github.com/etemiz/llama.garden), the policy Quartz's
 * `llamagarden` package leaves to the app.
 */
object LlamaGardenDefaults {
    /**
     * The viewer's whitelisted curators (`NPUBS` in `waifu-magnet-22.html`): their listings and their
     * `llama.garden` approvals make the catalog, and only their releases update the viewer.
     */
    val CURATORS: Set<HexKey> =
        setOf(
            "113bfab97549f43fa0ce90adb6ef03f3f32ce75a17b034ec94684c4ccd62c3c5",
            "77649e3424344dc6a068fc8f29265336d84b2b8f9449698609843427d468c341",
        )

    /**
     * Leading zero bits to mine into listings, model requests and seeder requests. The viewer mines
     * for 10 seconds with no target, and its events on relays carry about 20 bits.
     */
    const val POW_BITS = 20

    /** Largest `.torrent` file the app downloads to check its hash. The catalog's run to tens of KB. */
    const val MAX_TORRENT_FILE_BYTES = 16 * 1024 * 1024

    fun isCurator(pubKey: HexKey) = pubKey in CURATORS
}

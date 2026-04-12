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
package com.vitorpamplona.amethyst.ui.tor

typealias TorSettings = com.vitorpamplona.amethyst.commons.ui.tor.TorSettings

typealias TorType = com.vitorpamplona.amethyst.commons.ui.tor.TorType

typealias TorPresetType = com.vitorpamplona.amethyst.commons.ui.tor.TorPresetType

fun parseTorType(code: Int?) =
    com.vitorpamplona.amethyst.commons.ui.tor
        .parseTorType(code)

fun parseTorPresetType(code: Int?) =
    com.vitorpamplona.amethyst.commons.ui.tor
        .parseTorPresetType(code)

fun isPreset(
    torSettings: TorSettings,
    preset: TorSettings,
) = com.vitorpamplona.amethyst.commons.ui.tor
    .isPreset(torSettings, preset)

fun whichPreset(torSettings: TorSettings) =
    com.vitorpamplona.amethyst.commons.ui.tor
        .whichPreset(torSettings)

val torOnlyWhenNeededPreset = com.vitorpamplona.amethyst.commons.ui.tor.torOnlyWhenNeededPreset

val torDefaultPreset = com.vitorpamplona.amethyst.commons.ui.tor.torDefaultPreset

val torSmallPayloadsPreset = com.vitorpamplona.amethyst.commons.ui.tor.torSmallPayloadsPreset

val torFullyPrivate = com.vitorpamplona.amethyst.commons.ui.tor.torFullyPrivate

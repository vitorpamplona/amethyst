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
package com.vitorpamplona.amethyst.commons.moderation

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Settings for the hashtag-spam filter, provided at App() root by each
 * front end (Desktop, Android). Defaults to an error so missing provision
 * fails loudly during development; production binaries always wire this.
 */
val LocalHashtagSpamSettings: ProvidableCompositionLocal<HashtagSpamSettings> =
    compositionLocalOf { error("LocalHashtagSpamSettings not provided. Wire it at App() root.") }

/**
 * Pubkeys exempted from the hashtag-spam check — the active account's
 * follow set union the active account's own pubkey. Updated by the App()
 * root whenever the active account changes. Defaults to empty (strict
 * filter applies to everyone) so leaf composables can read it without a
 * null check.
 */
val LocalSpamExemptKeys: ProvidableCompositionLocal<Set<HexKey>> =
    compositionLocalOf { emptySet() }

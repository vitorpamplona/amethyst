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

import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.tor.TorPresetType
import com.vitorpamplona.amethyst.commons.tor.TorType

// Re-export shared types so existing Android imports continue to work
// The canonical types now live in commons/commonMain
@Suppress("unused")
private const val RE_EXPORTS = 0

// Android-specific resource ID mappings for TorType
val TorType.resourceId: Int
    get() =
        when (this) {
            TorType.OFF -> R.string.tor_off
            TorType.INTERNAL -> R.string.tor_internal
            TorType.EXTERNAL -> R.string.tor_external
        }

// Android-specific resource ID mappings for TorPresetType
val TorPresetType.resourceId: Int
    get() =
        when (this) {
            TorPresetType.ONLY_WHEN_NEEDED -> R.string.tor_when_needed
            TorPresetType.DEFAULT -> R.string.tor_default
            TorPresetType.SMALL_PAYLOADS -> R.string.tor_small_payloads
            TorPresetType.FULL_PRIVACY -> R.string.tor_full_privacy
            TorPresetType.CUSTOM -> R.string.tor_custom
        }

val TorPresetType.explainerId: Int
    get() =
        when (this) {
            TorPresetType.ONLY_WHEN_NEEDED -> R.string.tor_when_needed_explainer
            TorPresetType.DEFAULT -> R.string.tor_default_explainer
            TorPresetType.SMALL_PAYLOADS -> R.string.tor_small_payloads_explainer
            TorPresetType.FULL_PRIVACY -> R.string.tor_full_privacy_explainer
            TorPresetType.CUSTOM -> R.string.tor_custom_explainer
        }

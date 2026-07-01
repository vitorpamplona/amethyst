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
package com.vitorpamplona.amethyst.desktop

import com.vitorpamplona.amethyst.commons.service.upload.CompressionQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Image upload compression settings, persisted via [DesktopPreferences].
 * Wraps the raw prefs in `StateFlow` so Compose can react when the user
 * flips the toggles in the Media settings panel.
 *
 * Mirrors the [SearchHistoryStore] pattern: object singleton, init
 * loads from prefs, setters write through to both StateFlow and
 * prefs in one shot.
 *
 * Defaults: [CompressionQuality.DESKTOP_HIGH] (the new 1920px preset)
 * and `stripExif = true` (privacy-first — turn off only to keep
 * camera/lens metadata on photography uploads).
 */
object ImageCompressionStore {
    private val _quality = MutableStateFlow(CompressionQuality.fromName(DesktopPreferences.imageQualityRaw))
    val quality: StateFlow<CompressionQuality> = _quality.asStateFlow()

    private val _stripExif = MutableStateFlow(DesktopPreferences.imageStripExif)
    val stripExif: StateFlow<Boolean> = _stripExif.asStateFlow()

    fun setQuality(quality: CompressionQuality) {
        _quality.value = quality
        DesktopPreferences.imageQualityRaw = quality.name
    }

    fun setStripExif(value: Boolean) {
        _stripExif.value = value
        DesktopPreferences.imageStripExif = value
    }
}

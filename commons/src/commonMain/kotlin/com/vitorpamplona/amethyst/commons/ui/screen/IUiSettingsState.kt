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
package com.vitorpamplona.amethyst.commons.ui.screen

import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-agnostic interface for UI settings state.
 *
 * Exposes connectivity-aware display settings and feature-set mode queries.
 * Composables in the commons module can depend on this interface instead of
 * the concrete Android UiSettingsState class.
 */
interface IUiSettingsState {
    // ── connectivity-aware display settings (StateFlow<Boolean>) ─────

    /** Whether to show profile pictures (respects connectivity/user preference). */
    val showProfilePictures: StateFlow<Boolean>

    /** Whether to show URL previews (respects connectivity/user preference). */
    val showUrlPreview: StateFlow<Boolean>

    /** Whether to auto-start video playback (respects connectivity/user preference). */
    val startVideoPlayback: StateFlow<Boolean>

    /** Whether to show images inline (respects connectivity/user preference). */
    val showImages: StateFlow<Boolean>

    /** Whether the device is on a mobile/metered connection. */
    val isMobileOrMeteredConnection: StateFlow<Boolean>

    // ── feature-set mode queries ─────────────────────────────────────

    /** True when the UI is in performance (minimal) mode. */
    fun isPerformanceMode(): Boolean

    /** True when the UI is NOT in performance mode. */
    fun isNotPerformanceMode(): Boolean

    /** True when the UI is in complete (all features) mode. */
    fun isCompleteUIMode(): Boolean

    /** True when immersive scrolling (auto-hide nav bars) is active. */
    fun isImmersiveScrollingActive(): Boolean

    /** True when the modern gallery style is selected. */
    fun modernGalleryStyle(): Boolean

    // ── convenience snapshot accessors ───────────────────────────────

    /** Snapshot of [showProfilePictures]. */
    fun showProfilePictures(): Boolean

    /** Snapshot of [showUrlPreview]. */
    fun showUrlPreview(): Boolean

    /** Snapshot of [startVideoPlayback]. */
    fun startVideoPlayback(): Boolean

    /** Snapshot of [showImages]. */
    fun showImages(): Boolean
}

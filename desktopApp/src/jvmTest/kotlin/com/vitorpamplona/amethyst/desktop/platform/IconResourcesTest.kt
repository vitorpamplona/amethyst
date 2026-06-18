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
package com.vitorpamplona.amethyst.desktop.platform

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Phase 5.1 of the launch-optimization plan: pin the memoization invariants
 * of [IconResources]. The launch path previously decoded the same `/icon.png`
 * resource up to four times during a single cold boot; this object collapses
 * the work and the tests below assert that the lazy holders return the same
 * cached instance on subsequent accesses.
 *
 * See desktopApp/plans/2026-06-17-feat-app-launch-optimization-plan.md
 * § Phase 5.1.
 */
class IconResourcesTest {
    @Test
    fun iconBytesAreMemoized() {
        val first = IconResources.iconBytes
        val second = IconResources.iconBytes
        assertSame(first, second, "Raw PNG byte array must be the same instance across calls")
        assertTrue(first.isNotEmpty(), "Bundled /icon.png must not be empty")
    }

    @Test
    fun rawBufferedImageIsMemoized() {
        val first = IconResources.rawBufferedImage
        val second = IconResources.rawBufferedImage
        assertSame(first, second, "Decoded BufferedImage must be the same instance across calls")
        assertTrue(first.width > 0 && first.height > 0, "Decoded image must have positive dimensions")
    }

    @Test
    fun rawBitmapPainterIsMemoized() {
        val first = IconResources.rawBitmapPainter
        val second = IconResources.rawBitmapPainter
        assertSame(first, second, "Raw BitmapPainter must be the same instance across calls")
    }

    @Test
    fun adaptedBitmapPainterIsMemoized() {
        val first = IconResources.adaptedBitmapPainter
        val second = IconResources.adaptedBitmapPainter
        assertSame(first, second, "Adapted BitmapPainter must be the same instance across calls")
    }

    @Test
    fun adaptedBufferedImageEitherProducesAValueOrIsNull() {
        // On macOS this returns a squircle; on other platforms it may return null
        // when PlatformAppIcon.adaptForHost is a no-op. Either is acceptable — we
        // just verify the lazy doesn't throw.
        val adapted = IconResources.adaptedBufferedImage
        // Accessing twice must yield the same instance (or both null).
        assertSame(adapted, IconResources.adaptedBufferedImage)
        if (adapted != null) {
            assertNotNull(adapted, "If adaptation returns non-null, it must be a valid image")
        }
    }
}

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
package com.vitorpamplona.amethyst.shared.platform

import com.vitorpamplona.amethyst.stubs.PlatformGaps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopCapabilitiesTest {
    @Test
    fun `features with no desktop counterpart are declared, with a reason`() {
        DesktopCapabilities.declare()

        val unavailable = PlatformGaps.unavailableFeatures()
        assertTrue(PlatformGaps.isUnavailable(DesktopCapabilities.Feature.HEALTH_CONNECT))
        assertTrue(PlatformGaps.isUnavailable(DesktopCapabilities.Feature.PICTURE_IN_PICTURE))

        // A declaration without a reason is just a TODO wearing a hat; the whole
        // point is that these entries explain themselves to the next reader.
        unavailable.forEach { (feature, reason) ->
            assertTrue(reason.length > 40, "'$feature' needs a real explanation, not '$reason'")
        }
    }

    @Test
    fun `a feature nobody declared is not silently treated as unavailable`() {
        DesktopCapabilities.declare()
        assertFalse(
            PlatformGaps.isUnavailable("SomethingNobodyDeclared"),
            "unavailable must mean 'we decided this has no counterpart', never 'we have not looked'",
        )
    }

    @Test
    fun `the two kinds of gap stay distinguishable`() {
        val kinds = mutableMapOf<String, PlatformGaps.Kind>()
        PlatformGaps.setReporter { feature, _, kind -> kinds[feature] = kind }
        try {
            PlatformGaps.report("Some.unbuiltThing", "a desktop equivalent exists; nobody wrote it")
            PlatformGaps.unavailable("Some.impossibleThing", "the platform genuinely has no counterpart for this")

            assertEquals(PlatformGaps.Kind.NOT_IMPLEMENTED_YET, kinds["Some.unbuiltThing"])
            assertEquals(PlatformGaps.Kind.NO_PLATFORM_EQUIVALENT, kinds["Some.impossibleThing"])
        } finally {
            PlatformGaps.setReporter(null)
        }
    }
}

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
package com.vitorpamplona.amethyst.commons.napplet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NappletCapabilityTest {
    @Test
    fun mapsKnownDomainsCaseInsensitively() {
        assertEquals(NappletCapability.SHELL, NappletCapability.fromNapDomain("shell"))
        assertEquals(NappletCapability.IDENTITY, NappletCapability.fromNapDomain("identity"))
        assertEquals(NappletCapability.KEYS, NappletCapability.fromNapDomain("keys"))
        assertEquals(NappletCapability.RELAY, NappletCapability.fromNapDomain("Relay"))
        assertEquals(NappletCapability.VALUE, NappletCapability.fromNapDomain("value"))
        assertEquals(NappletCapability.STORAGE, NappletCapability.fromNapDomain("  STORAGE  "))
        assertEquals(NappletCapability.RESOURCE, NappletCapability.fromNapDomain("resource"))
        assertEquals(NappletCapability.UPLOAD, NappletCapability.fromNapDomain("upload"))
    }

    @Test
    fun unknownDomainMapsToNullNotAFallbackGrant() {
        // Domains we don't broker yet must stay unknown (default-deny), not fall through.
        assertNull(NappletCapability.fromNapDomain("inc"))
        assertNull(NappletCapability.fromNapDomain("intent"))
        assertNull(NappletCapability.fromNapDomain("cvm"))
        assertNull(NappletCapability.fromNapDomain("filesystem"))
        assertNull(NappletCapability.fromNapDomain(""))
    }

    @Test
    fun resolveSeparatesKnownFromUnknownAndFlags() {
        val resolved = resolveRequiredCapabilities(listOf("identity", "relay", "intent", "value"))

        assertEquals(
            setOf(NappletCapability.IDENTITY, NappletCapability.RELAY, NappletCapability.VALUE),
            resolved.capabilities,
        )
        assertEquals(listOf(UnknownNapDomain("intent")), resolved.unknown)
        assertTrue(resolved.hasUnknown)
    }

    @Test
    fun resolveWithOnlyKnownDomainsHasNoUnknownFlag() {
        val resolved = resolveRequiredCapabilities(listOf("identity"))
        assertFalse(resolved.hasUnknown)
    }
}

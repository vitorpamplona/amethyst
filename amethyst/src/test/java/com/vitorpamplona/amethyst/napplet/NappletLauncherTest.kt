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
package com.vitorpamplona.amethyst.napplet

import android.content.Context
import android.content.Intent
import com.vitorpamplona.amethyst.commons.napplet.HostProfile
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertNull
import org.junit.Test

class NappletLauncherTest {
    private val context = mockk<Context>(relaxed = true)
    private val manifest = mockk<NappletManifest>()
    private val author = "aa".repeat(32)

    @Test
    fun manifestLaunchRejectsNonEventManifest() {
        NappletLauncher.launch(context, manifest, author, "demo")

        verify(exactly = 0) { context.startActivity(any<Intent>()) }
    }

    @Test
    fun embeddedBuildLaunchParamsRejectsNonEventManifest() {
        assertNull(NappletLauncher.buildLaunchParams(context, manifest, author, "demo"))
    }

    @Test
    fun rawLaunchRejectsNappletProfile() {
        every { context.startActivity(any<Intent>()) } returns Unit

        NappletLauncher.launch(
            context = context,
            paths = emptyList(),
            servers = emptyList(),
            authorPubKey = author,
            identifier = "demo",
            aggregateHash = null,
            title = "Demo",
            requires = emptyList(),
            profile = HostProfile.NAPPLET,
        )

        verify(exactly = 0) { context.startActivity(any<Intent>()) }
    }
}

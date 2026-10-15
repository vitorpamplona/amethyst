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
package com.vitorpamplona.amethyst.cli.commands

import com.vitorpamplona.amethyst.commons.appupdate.FlatpakReleaseEvents
import com.vitorpamplona.amethyst.commons.appupdate.OrchardConfig
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The repo's Orchard TOML, read as `amy releases publish` reads it, gives the listing orchard-publish
 * signed from the same file (relay.orchard.rocks, Amethyst 1.19.0).
 */
class OrchardTomlTest {
    private val toml = listOf(File("../desktopApp/packaging/orchard"), File("desktopApp/packaging/orchard")).first { it.isDirectory }.resolve("com.vitorpamplona.amethyst.Amethyst.publish.toml")

    @Test
    fun theListingIsOrchardPublishs() {
        val config = OrchardConfig.fromMap(ReleasesCommands.readToml(toml)) { File(toml.parentFile, it).takeIf(File::isFile)?.readText() }
        assertEquals(listOf("wss://relay.orchard.rocks", "wss://nos.lol", "wss://relay.primal.net"), config.relays)
        assertEquals("Release notes: https://github.com/vitorpamplona/amethyst/releases", config.notes)

        val listing = NostrSignerSync().sign(FlatpakReleaseEvents.listing(config))
        assertEquals(File(toml.parentFile, "description.md").readText(), listing.content)
        assertEquals(
            listOf(
                listOf("d", "com.vitorpamplona.amethyst.Amethyst"),
                listOf("name", "Amethyst"),
                listOf("summary", "Join the social network you control"),
                listOf("icon", "https://raw.githubusercontent.com/vitorpamplona/amethyst/main/desktopApp/packaging/flatpak/icons/512/com.vitorpamplona.amethyst.Amethyst.png"),
                listOf("license", "MIT AND LGPL-2.1-or-later AND BSD-2-Clause AND Apache-2.0 AND GPL-2.0-only WITH Classpath-exception-2.0"),
                listOf("repository", "https://github.com/vitorpamplona/amethyst"),
                listOf("h", "https://amethyst.social"),
                listOf("t", "Network"),
                listOf("t", "InstantMessaging"),
                listOf("f", "linux-x86_64"),
                listOf("f", "linux-aarch64"),
            ),
            listing.tags.map { it.toList() },
        )
    }
}

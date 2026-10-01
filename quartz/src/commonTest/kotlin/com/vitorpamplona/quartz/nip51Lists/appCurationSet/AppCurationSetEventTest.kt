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
package com.vitorpamplona.quartz.nip51Lists.appCurationSet

import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.shared.Platform
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppCurationSetEventTest {
    private val zapstoreGames =
        """
        {"id":"0389c8b10882df52226b116745e65060561d5165abd842953d9a7bc466cae479","pubkey":"aa1f96f685d0ac3e28a52feb87a20399a91afb3ac3137afeb7698dfcc99bc454","created_at":1775252298,"kind":30267,"tags":[["name","Games"],["d","games"],["f","android-arm64-v8a"],["h","acfeaea6e51420e8068fac446ca9d17d7a9ef6a5d20d93894e50fee3d4902a84"],["a","32267:78ce6faa72264387284e647ba6938995735ec8c7d5c5a65737e55130f026307d:com.gitlab.ardash.appleflinger.android"],["a","32267:78ce6faa72264387284e647ba6938995735ec8c7d5c5a65737e55130f026307d:com.adilhanney.ricochlime"],["a","32267:78ce6faa72264387284e647ba6938995735ec8c7d5c5a65737e55130f026307d:net.minetest.minetest"],["a","32267:78ce6faa72264387284e647ba6938995735ec8c7d5c5a65737e55130f026307d:de.rainerhock.eightbitwonders"],["a","32267:78ce6faa72264387284e647ba6938995735ec8c7d5c5a65737e55130f026307d:org.moire.opensudoku"],["a","32267:78ce6faa72264387284e647ba6938995735ec8c7d5c5a65737e55130f026307d:de.chadenas.cpudefense"],["a","32267:78ce6faa72264387284e647ba6938995735ec8c7d5c5a65737e55130f026307d:de.sesu8642.feudaltactics"],["a","32267:78ce6faa72264387284e647ba6938995735ec8c7d5c5a65737e55130f026307d:com.peaceray.codeword"],["a","32267:78ce6faa72264387284e647ba6938995735ec8c7d5c5a65737e55130f026307d:com.serwylo.retrowars"],["a","32267:78ce6faa72264387284e647ba6938995735ec8c7d5c5a65737e55130f026307d:de.mlex.same"],["a","32267:78ce6faa72264387284e647ba6938995735ec8c7d5c5a65737e55130f026307d:com.github.thewierdnut.endless_mobile"],["a","32267:78ce6faa72264387284e647ba6938995735ec8c7d5c5a65737e55130f026307d:com.tacticmaster"],["a","32267:78ce6faa72264387284e647ba6938995735ec8c7d5c5a65737e55130f026307d:io.github.lime3ds.android"],["a","32267:78ce6faa72264387284e647ba6938995735ec8c7d5c5a65737e55130f026307d:com.unciv.app"],["a","32267:78ce6faa72264387284e647ba6938995735ec8c7d5c5a65737e55130f026307d:org.wesnoth.Wesnoth"],["a","32267:78ce6faa72264387284e647ba6938995735ec8c7d5c5a65737e55130f026307d:com.agateau.burgerparty"],["a","32267:b95162b5280fa639f779d1e96d05bfa4a00159214c2a578b5244b2fb75b642d3:com.magius.lightningreaction"],["a","32267:78ce6faa72264387284e647ba6938995735ec8c7d5c5a65737e55130f026307d:de.z11.roboyard"],["a","32267:78ce6faa72264387284e647ba6938995735ec8c7d5c5a65737e55130f026307d:me.lecaro.breakout"]],"content":"","sig":"b46c90094ba55ea46e86dba9e3ec47f77b6e7369e0244644da7d1b4a3d13031d9a385861d976529a47887ed2063bcbc8176e6dc40d3b305fddd81c2688a7b38e"}
        """.trimIndent()

    @Test
    fun parsesZapstoreCurationSet() {
        val event = Event.fromJson(zapstoreGames)

        assertIs<AppCurationSetEvent>(event)
        assertTrue(event.verify())

        assertEquals("games", event.dTag())
        // Zapstore names the set with `name`, not NIP-51's `title`.
        assertNull(event.title())
        assertEquals("Games", event.titleOrName())
        assertNull(event.description())
        assertEquals(listOf(Platform.ANDROID_ARM64_V8A), event.platforms())

        val apps = event.apps()
        assertEquals(19, apps.size)
        assertTrue(apps.all { it.address.kind == SoftwareApplicationEvent.KIND })
        assertEquals("com.gitlab.ardash.appleflinger.android", apps.first().address.dTag)
        assertEquals("me.lecaro.breakout", apps.last().address.dTag)
        assertEquals(
            "b95162b5280fa639f779d1e96d05bfa4a00159214c2a578b5244b2fb75b642d3",
            apps.single { it.address.dTag == "com.magius.lightningreaction" }.address.pubKeyHex,
        )

        assertEquals("Games", event.indexableContent())
    }

    @Test
    fun titleWinsOverName() {
        val template =
            AppCurationSetEvent.build(title = "Nostr apps", dTag = "nostr", createdAt = 1729302793) {
                add(arrayOf("name", "ignored"))
            }
        val tags = template.tags
        val event = AppCurationSetEvent("0".repeat(64), "1".repeat(64), template.createdAt, tags, "", "2".repeat(128))

        assertEquals("Nostr apps", event.titleOrName())
        assertEquals("ignored", event.name())
    }
}

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
package com.vitorpamplona.amethyst.commons.claudecode

import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.BooleanType
import com.vitorpamplona.amethyst.commons.model.FontSizeType
import com.vitorpamplona.amethyst.commons.model.ThemeType
import com.vitorpamplona.amethyst.commons.model.UiSettingsFlow
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.settings.registry.AmethystSettings
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SettingsToolServerTest {
    private val ui = UiSettingsFlow()
    private val account = AccountSettings(KeyPair())
    private val opened = mutableListOf<Route>()
    private val server = SettingsToolServer({ AmethystSettings.registry(ui, account) }, { opened.add(it) })

    private fun args(json: String) = Json.parseToJsonElement(json).jsonObject

    @Test
    fun theWordsPeopleUseFindTheSetting() {
        val registry = AmethystSettings.registry(ui, account)
        assertEquals("app.theme", registry.search("dark mode").first().id)
        assertEquals("app.font_size", registry.search("bigger text").first().id)
        assertTrue(registry.search("mobile data").map { it.id }.contains("app.start_playback"))
        assertTrue(registry.search("notifications").any { it.id == "account.notifications_split" })
        // As the assistant asks: whole phrases, with words no setting has.
        assertEquals("app.font_size", registry.search("text size font size too small to read").first().id)
        assertTrue(registry.search("appearance theme display").map { it.id }.contains("app.theme"))
        assertTrue(registry.search("xyzzy plugh").isEmpty())
    }

    @Test
    fun keysWalletsAndRelaysAreOutOfReach() {
        val ids = AmethystSettings.registry(ui, account).settings.map { it.id }
        listOf("nip46", "bunker", "nwc", "wallet_connect", "private", "key", "relay_list", "payment_target").forEach { word ->
            assertTrue(ids.none { it.contains(word) }, "$word is exposed: $ids")
        }
        // Every id is unique: a duplicate would make one of them unreachable.
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun changesGoThroughTheSameStateTheScreensUse() =
        runBlocking {
            val result = server.call(SettingsToolServer.CHANGE, args("""{"id":"app.theme","value":"DARK"}"""))
            assertTrue(!result.isError, result.text)
            assertEquals(ThemeType.DARK, ui.theme.value)
            assertTrue(result.text.contains("was SYSTEM"), result.text)

            server.call(SettingsToolServer.CHANGE, args("""{"id":"app.drafts","value":"false"}"""))
            assertEquals(BooleanType.NEVER, ui.automaticallyCreateDrafts.value)

            server.call(SettingsToolServer.CHANGE, args("""{"id":"account.relay_login_message_strangers","value":"true"}"""))
            assertEquals(true, account.relayAuthTrustMessageStrangers.value)
        }

    @Test
    fun aValueTheSettingCantTakeIsRefusedAndNothingChanges() =
        runBlocking {
            val result = server.call(SettingsToolServer.CHANGE, args("""{"id":"app.font_size","value":"GIGANTIC"}"""))
            assertTrue(result.isError)
            assertTrue(result.text.contains("HUGE"), result.text)
            assertEquals(FontSizeType.NORMAL, ui.fontSize.value)

            assertTrue(server.call(SettingsToolServer.CHANGE, args("""{"id":"app.nope","value":"x"}""")).isError)
        }

    @Test
    fun theApprovalSaysWhatWouldChange() {
        assertEquals(
            "Theme: SYSTEM (follow the system) → DARK (always dark)",
            server.describe(SettingsToolServer.CHANGE, args("""{"id":"app.theme","value":"DARK"}""")),
        )
        // Describing doesn't change anything.
        assertEquals(ThemeType.SYSTEM, ui.theme.value)
    }

    @Test
    fun explainingAndOpeningAScreen() =
        runBlocking {
            val text = server.call(SettingsToolServer.GET, args("""{"id":"account.relay_login_message_strangers"}""")).text
            assertTrue(text.contains("Careful"), text)
            assertTrue(text.contains("Applies to this account"), text)

            server.call(SettingsToolServer.OPEN, args("""{"id":"app.font_size"}"""))
            assertEquals(Route.Settings, assertNotNull(opened.single()))
        }
}

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
package com.vitorpamplona.amethyst.cli

import com.fasterxml.jackson.databind.JsonNode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * `amy status` answers two questions — who is signed in, and what each of
 * them has saved — so these pin both: the `--json` contract that carries the
 * answers, and the promises that make the command safe to run blind (works
 * with zero or many accounts, never prompts, never writes).
 *
 * The text rendering is deliberately NOT asserted on beyond "the answer is
 * in there": per `cli/DEVELOPMENT.md` only the JSON shape is public API.
 */
class StatusCommandTest {
    private fun statusJson(): JsonNode {
        val r = amy("--json", "status")
        assertEquals(0, r.exit, "status should always exit 0, stderr: ${r.stderr}")
        assertEquals(1, r.stdoutLines.size, "expected exactly one stdout line, got: ${r.stdout}")
        return Output.mapper.readTree(r.stdoutLines.single())
    }

    private fun initAccount(name: String) {
        val r = amy("--secret-backend", "plaintext", "--account", name, "init")
        assertEquals(0, r.exit, "init $name failed: ${r.stderr}")
    }

    private fun accountNamed(
        json: JsonNode,
        name: String,
    ): JsonNode = assertNotNull(json["accounts"].firstOrNull { it["name"].asText() == name }, "no account '$name' in $json")

    @Test
    fun reportsNoAccountsWithoutFailing() =
        withSharedAmyHome {
            val json = statusJson()
            assertTrue(json["accounts"].isEmpty, "a fresh machine has no accounts: $json")
            assertTrue(json["current"].isNull, "nothing can be current when nothing exists: $json")
        }

    /**
     * The read-only promise. `status` is the command a confused user runs
     * first, so it must not leave an event store (or anything else) behind on
     * a machine that has none.
     */
    @Test
    fun writesNothingToDisk() =
        withSharedAmyHome { home ->
            assertEquals(0, amy("--json", "status").exit)
            assertFalse(File(home, ".amy").exists(), "status must not create ~/.amy")
        }

    @Test
    fun namesEveryAccountAndMarksTheCurrentOne() =
        withSharedAmyHome {
            initAccount("alice")
            initAccount("bob")
            assertEquals(0, amy("use", "alice").exit)

            val json = statusJson()
            assertEquals("alice", json["current"].asText())
            assertEquals(listOf("alice", "bob"), json["accounts"].map { it["name"].asText() })
            assertTrue(accountNamed(json, "alice")["current"].asBoolean())
            assertFalse(accountNamed(json, "bob")["current"].asBoolean())
        }

    @Test
    fun reportsHowEachAccountSigns() =
        withSharedAmyHome {
            initAccount("alice")

            val alice = accountNamed(statusJson(), "alice")
            assertEquals("local", alice["signer"].asText())
            assertEquals("plaintext", alice["key_storage"].asText())
            assertTrue(alice["can_sign"].asBoolean())
            assertTrue(alice["npub"].asText().startsWith("npub1"))
            assertEquals(64, alice["pubkey"].asText().length)
        }

    /**
     * A brand-new account has saved nothing — including no contacts. `init`
     * writes a self-alias so you can name your own account; counting it made
     * every fresh account claim an address book it doesn't have.
     */
    @Test
    fun freshAccountHasSavedNothing() =
        withSharedAmyHome {
            initAccount("alice")

            val saved = accountNamed(statusJson(), "alice")["saved"]
            assertEquals(0, saved["contacts"].asInt(), "the self-alias is not a saved contact")
            assertEquals(0, saved["events"].asInt())
            assertEquals(0, saved["follows"].asInt())
            assertEquals(0, saved["relays"].asInt())
            assertEquals(0, saved["dm_relays"].asInt())
            assertEquals(0, saved["marmot_groups"].asInt())
            assertEquals(0, saved["marmot_messages"].asInt())
            assertEquals(0, saved["concord_communities"].asInt())
            assertFalse(saved["key_package"].asBoolean())
            assertFalse(saved["cashu_wallet"].asBoolean())
            assertTrue(saved["dm_cursor_at"].isNull)

            val text = amy("status").stdout
            assertTrue(text.contains("nothing yet"), "text should say so plainly: $text")
        }

    @Test
    fun countsSavedContactsOnceTheAddressBookGrows() =
        withSharedAmyHome { home ->
            initAccount("alice")
            val aliases = File(home, ".amy/alice/aliases.json")
            val map = Output.mapper.readValue(aliases, Map::class.java).toMutableMap()
            map["bob"] = "npub1ngs0702f9mzph9j8csd22vj8ycg97q3jxzcs4qvdymmmet079nqsr4f60n"
            aliases.writeText(Output.mapper.writeValueAsString(map))

            assertEquals(1, accountNamed(statusJson(), "alice")["saved"]["contacts"].asInt())
        }

    /**
     * The selection state is the thing only `status` can report. Every other
     * verb resolves an account first and dies; a user with several accounts
     * and no pin needs to be told that, not left to read a list.
     */
    @Test
    fun warnsWhenSeveralAccountsAndNoPin() =
        withSharedAmyHome {
            initAccount("alice")
            initAccount("bob")

            val json = statusJson()
            assertTrue(json["current"].isNull)
            assertFalse(json["current_exists"].asBoolean())
            assertTrue(amy("status").stdout.contains("No account selected"), "status should say nothing is selected")
        }

    /**
     * A pin left behind by a deleted account makes every other verb fail with
     * "pins 'x' but … doesn't exist". Status has to name the same cause.
     */
    @Test
    fun warnsWhenTheCurrentPinIsStale() =
        withSharedAmyHome { home ->
            initAccount("alice")
            File(home, ".amy/current").writeText("ghost")

            val json = statusJson()
            assertEquals("ghost", json["current"].asText())
            assertFalse(json["current_exists"].asBoolean(), "the pinned directory is gone")

            val text = amy("status").stdout
            assertTrue(text.contains("No account selected"), "status should flag the broken pin: $text")
            assertTrue(text.contains("ghost"), "status should name the dangling pin: $text")
        }

    /** A single account, or a good pin, resolves on its own — no nagging. */
    @Test
    fun staysQuietWhenAnAccountResolves() =
        withSharedAmyHome {
            initAccount("alice")
            assertTrue(statusJson()["current"].isNull, "one account needs no pin")
            assertFalse(amy("status").stdout.contains("No account selected"))

            initAccount("bob")
            assertEquals(0, amy("use", "bob").exit)
            assertTrue(statusJson()["current_exists"].asBoolean())
            assertFalse(amy("status").stdout.contains("No account selected"), "a good pin is not a warning")
        }

    /** The machine-level operator key is reported, and absent by default. */
    @Test
    fun reportsNoOperatorKeyUntilOneExists() =
        withSharedAmyHome {
            initAccount("alice")
            assertTrue(statusJson()["operator"].isNull, "graperank was never run")
            assertFalse(amy("status").stdout.contains("operator"))
        }

    /** Text mode has no shape contract, but it must still carry the answers. */
    @Test
    fun textModeCarriesTheSameAnswers() =
        withSharedAmyHome {
            initAccount("alice")
            initAccount("bob")
            assertEquals(0, amy("use", "bob").exit)

            val json = statusJson()
            val text = amy("status").stdout
            assertTrue(text.contains("alice"), "text should name every account: $text")
            assertTrue(text.contains(accountNamed(json, "bob")["npub"].asText()), "text should print the npub: $text")
            assertTrue(text.contains("current"), "text should mark the active account: $text")
        }
}

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
package com.vitorpamplona.amethyst.commons.keystorage

import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PasswordKeyFileTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val key = "7f".repeat(32)
    private val password = "correct horse battery"

    // A cheap scrypt cost: the format and the flow are under test here, not NIP-49's KDF.
    private fun newFile(legacy: File? = null) =
        PasswordKeyFile(
            file = File(folder.root, "keys.ncryptsec"),
            legacyFile = legacy,
            legacyDecrypt = { c, p -> SecureKeyStorage.create().decryptData(c, p) },
            logN = 4,
        )

    @Test
    fun theFirstKeyAsksForAPasswordAndReadsBack() =
        runBlocking<Unit> {
            val script = ScriptedPrompts(NewPasswordAnswer.Password(password))
            val job = script.start(this)
            val file = newFile()

            file.put("npub1a", key, script.prompts)
            assertEquals(key, file.get("npub1a", script.prompts))
            assertTrue(file.isUnlocked)
            assertEquals(KeyStorageStatus.PasswordFile(hasPassword = true, unlocked = true), file.status())
            assertEquals(1, script.asked.size)
            job.cancel()
        }

    @Test
    fun aNewRunAsksOnceAndAgainAfterAWrongPassword() =
        runBlocking<Unit> {
            val setup = ScriptedPrompts(NewPasswordAnswer.Password(password))
            val setupJob = setup.start(this)
            newFile().put("npub1a", key, setup.prompts)
            setupJob.cancel()

            val script = ScriptedPrompts("wrong password", password)
            val job = script.start(this)
            val file = newFile()
            assertFalse(file.isUnlocked)
            assertEquals(key, file.get("npub1a", script.prompts))
            assertEquals(listOf(false, true), script.asked.map { (it as KeyPrompt.Unlock).wrongPassword })
            // Unlocked for the run: no more questions.
            assertEquals(key, file.get("npub1a", script.prompts))
            assertEquals(2, script.asked.size)
            job.cancel()
        }

    @Test
    fun dontRememberKeepsNothingAndDoesNotAskAgain() =
        runBlocking<Unit> {
            val script = ScriptedPrompts(NewPasswordAnswer.DontRemember)
            val job = script.start(this)
            val file = newFile()

            assertFailsWith<KeyNotKeptException> { file.put("npub1a", key, script.prompts) }
            assertFailsWith<KeyNotKeptException> { file.put("npub1b", key, script.prompts) }
            assertEquals(1, script.asked.size)
            assertEquals(KeyStorageStatus.NotKept, file.status())
            assertFalse(File(folder.root, "keys.ncryptsec").exists())
            job.cancel()
        }

    @Test
    fun skippingTheUnlockReadsNothingForTheRun() =
        runBlocking<Unit> {
            val setup = ScriptedPrompts(NewPasswordAnswer.Password(password))
            val setupJob = setup.start(this)
            newFile().put("npub1a", key, setup.prompts)
            setupJob.cancel()

            val script = ScriptedPrompts(null)
            val job = script.start(this)
            val file = newFile()
            assertNull(file.get("npub1a", script.prompts))
            assertNull(file.get("npub1a", script.prompts))
            assertEquals(1, script.asked.size)
            assertIs<KeyStorageStatus.PasswordFile>(file.status())
            job.cancel()
        }

    @Test
    fun withNothingToAskNoKeyIsKept() =
        runBlocking<Unit> {
            assertFailsWith<SecureStorageException> { newFile().put("npub1a", key, null) }
        }

    @Test
    fun onlyPrivateKeysFit() =
        runBlocking<Unit> {
            val script = ScriptedPrompts(NewPasswordAnswer.Password(password))
            val job = script.start(this)
            assertFailsWith<SecureStorageException> { newFile().put("metadata", "not a key", script.prompts) }
            assertTrue(script.asked.isEmpty())
            job.cancel()
        }

    @Test
    fun theOldConsoleFileMovesIntoTheNewOneOnUnlock() =
        runBlocking<Unit> {
            val legacy = File(folder.root, "keys.enc")
            legacy.writeText("npub1old:" + SecureKeyStorage.create().encryptData(key, password))

            val script = ScriptedPrompts(password)
            val job = script.start(this)
            val file = newFile(legacy)
            assertTrue(file.hasPassword())

            file.unlock(script.prompts)
            assertEquals(key, file.get("npub1old", script.prompts))
            assertFalse(legacy.exists())
            assertTrue(File(folder.root, "keys.enc.migrated").exists())
            job.cancel()
        }

    @Test
    fun deletingTheLastKeyRemovesTheFile() =
        runBlocking<Unit> {
            val script = ScriptedPrompts(NewPasswordAnswer.Password(password))
            val job = script.start(this)
            val file = newFile()
            file.put("npub1a", key, script.prompts)
            assertTrue(file.delete("npub1a"))
            assertFalse(file.delete("npub1a"))
            assertFalse(File(folder.root, "keys.ncryptsec").exists())
            job.cancel()
        }
}

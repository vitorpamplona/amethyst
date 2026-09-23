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
package com.vitorpamplona.amethyst.commons.model.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Round-trip coverage for the encrypted store.
 *
 * The store shipped with `decrypt` reading
 * `encryption.decrypt(...).contentToString()`, which renders a ByteArray as
 * `"[104, 101, 108]"` instead of decoding it, so every value read back was the
 * debug rendering of its own bytes. Nothing caught it because the class had no
 * callers. [saveAndGetRoundTrips] is the test that fails against that version.
 */
class EncryptedDataStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val key = stringPreferencesKey("nwc")

    private var seq = 0

    /**
     * A store over its own pair of fresh paths.
     *
     * The files are named, never created: DataStore writes them itself, and an
     * empty file left behind by `newFile()` is not a valid preferences_pb. The
     * path is resolved once and captured, because `produceFile` may be invoked
     * more than once and must answer the same file every time.
     */
    private fun store(scope: CoroutineScope): EncryptedDataStore {
        val n = seq++
        val dataFile = File(folder.root, "secrets_$n.preferences_pb")
        val keyFile = File(folder.root, "secret_$n.key")
        return EncryptedDataStore(
            PreferenceDataStoreFactory.createWithPath(scope = scope, produceFile = { dataFile.toOkioPath() }),
            SecretEncryption(keyFile),
            scope = scope,
        )
    }

    @Test
    fun saveAndGetRoundTrips() =
        runTest {
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val subject = store(scope)
            val nsec = "e5e2b1d3f6a94c8d7b0e1f2a3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d"

            subject.save(key, nsec)

            assertEquals(nsec, subject.get(key))
        }

    @Test
    fun missingKeyReadsBackAsNull() =
        runTest {
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            assertNull(store(scope).get(key))
        }

    @Test
    fun valuesAreNotStoredInPlaintext() =
        runTest {
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val file = File(folder.root, "plaintext_check.preferences_pb")
            val subject =
                EncryptedDataStore(
                    PreferenceDataStoreFactory.createWithPath(scope = scope, produceFile = { file.toOkioPath() }),
                    SecretEncryption(File(folder.root, "plaintext_check.key")),
                    scope = scope,
                )
            val secret = "correct-horse-battery-staple"

            subject.save(key, secret)

            val onDisk = file.readBytes().decodeToString()
            assertEquals("the secret must not be readable in the store file", false, onDisk.contains(secret))
        }

    @Test
    fun overwriteReplacesTheValue() =
        runTest {
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val subject = store(scope)

            subject.save(key, "first")
            subject.save(key, "second")

            assertEquals("second", subject.get(key))
        }

    @Test
    fun removeClearsTheValue() =
        runTest {
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val subject = store(scope)

            subject.save(key, "value")
            subject.remove(key)

            assertNull(subject.get(key))
        }
}

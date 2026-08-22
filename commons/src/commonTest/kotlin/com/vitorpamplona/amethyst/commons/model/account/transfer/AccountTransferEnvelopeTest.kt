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
package com.vitorpamplona.amethyst.commons.model.account.transfer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountTransferEnvelopeTest {
    // Real exports run at DEFAULT_LOG_N (2^16). Tests use the cheapest cost the
    // envelope accepts: scrypt is deliberately slow, and these assert the
    // format, not the KDF's parameters.
    private val testLogN = 1

    private val bundle =
        AccountTransferBundle(
            createdAt = 1_700_000_000,
            appVersion = "1.0.0",
            accounts =
                listOf(
                    AccountTransferEntry(
                        npub = "npub1vitor",
                        privKeyHex = "aa".repeat(32),
                        preferences =
                            mapOf(
                                "defaultZapAmount" to TransferValue.Int64(21),
                                "showSensitive" to TransferValue.Bool(true),
                                "nwcWallets" to TransferValue.Str("""[{"uri":"nostr+walletconnect://x"}]"""),
                                "hasDonatedInVersion" to TransferValue.StrSet(listOf("0.9", "1.0")),
                                "reportThreshold" to TransferValue.Int32(5),
                                "volume" to TransferValue.Flt(0.5f),
                            ),
                        cashuKeysetCounters = mapOf("009a1f293253e41e" to 42L),
                        marmotMessages = mapOf("ab".repeat(16) to listOf("""{"kind":9,"content":"hi"}""", """{"kind":9,"content":"there"}""")),
                    ),
                ),
            globalPreferences = mapOf("shared_settings" to TransferValue.Str("""{"theme":"DARK"}""")),
            sharedPreferences = mapOf("amethyst_global_settings" to mapOf("notification_service_enabled" to TransferValue.Bool(true))),
            files = mapOf("datastore/favorite_apps.preferences_pb" to "AAECAw=="),
        )

    @Test
    fun roundTripsEveryValueType() {
        val encrypted = AccountTransferEnvelope.encrypt(bundle, "correct horse", testLogN)
        val decrypted = AccountTransferEnvelope.decrypt(encrypted, "correct horse")

        assertEquals(bundle, decrypted)
    }

    @Test
    fun encryptsThePayload() {
        val encrypted = AccountTransferEnvelope.encrypt(bundle, "pw", testLogN)

        // The wallet string is the kind of thing that must not be readable in
        // the file, so assert on it rather than on a generic marker.
        assertFalse(encrypted.decodeToString().contains("nostr+walletconnect"))
        assertFalse(encrypted.decodeToString().contains("npub1vitor"))
        // Private group history is plaintext in the bundle; the envelope is the
        // only thing keeping it off disk in the clear.
        assertFalse(encrypted.decodeToString().contains("there"))
    }

    @Test
    fun roundTripsArchivedGroupHistoryInOrder() {
        val encrypted = AccountTransferEnvelope.encrypt(bundle, "pw", testLogN)
        val archive =
            AccountTransferEnvelope
                .decrypt(encrypted, "pw")
                .accounts
                .single()
                .marmotMessages

        // Order matters: the store replays these as a conversation.
        assertEquals(
            listOf("""{"kind":9,"content":"hi"}""", """{"kind":9,"content":"there"}"""),
            archive["ab".repeat(16)],
        )
    }

    @Test
    fun rejectsTheWrongPassword() {
        val encrypted = AccountTransferEnvelope.encrypt(bundle, "right", testLogN)

        assertFailsWith<AccountTransferEnvelope.InvalidTransferFile> {
            AccountTransferEnvelope.decrypt(encrypted, "wrong")
        }
    }

    @Test
    fun rejectsATamperedPayload() {
        val encrypted = AccountTransferEnvelope.encrypt(bundle, "pw", testLogN)
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1] + 1).toByte()

        assertFailsWith<AccountTransferEnvelope.InvalidTransferFile> {
            AccountTransferEnvelope.decrypt(encrypted, "pw")
        }
    }

    @Test
    fun rejectsATamperedSalt() {
        val encrypted = AccountTransferEnvelope.encrypt(bundle, "pw", testLogN)
        // Salt starts at offset 10; flipping it must fail authentication rather
        // than quietly deriving a different key.
        encrypted[10] = (encrypted[10] + 1).toByte()

        assertFailsWith<AccountTransferEnvelope.InvalidTransferFile> {
            AccountTransferEnvelope.decrypt(encrypted, "pw")
        }
    }

    @Test
    fun rejectsAnScryptCostDowngrade() {
        val encrypted = AccountTransferEnvelope.encrypt(bundle, "pw", logN = 4)
        // The cost byte is authenticated, so an attacker cannot rewrite it to 1
        // to make the file cheap to brute force.
        encrypted[9] = 1

        assertFailsWith<AccountTransferEnvelope.InvalidTransferFile> {
            AccountTransferEnvelope.decrypt(encrypted, "pw")
        }
    }

    @Test
    fun rejectsAnOutOfRangeScryptCostBeforeRunningIt() {
        val encrypted = AccountTransferEnvelope.encrypt(bundle, "pw", testLogN)
        encrypted[9] = 31

        val error =
            assertFailsWith<AccountTransferEnvelope.InvalidTransferFile> {
                AccountTransferEnvelope.decrypt(encrypted, "pw")
            }
        assertEquals("Unsupported encryption parameters", error.message)
    }

    @Test
    fun rejectsAFileThatIsNotATransferFile() {
        // Long enough to clear the length check, so this exercises the magic
        // header rather than the "too short" branch below it.
        val notABackup = "PK\u0003\u0004".encodeToByteArray() + ByteArray(200)

        val error =
            assertFailsWith<AccountTransferEnvelope.InvalidTransferFile> {
                AccountTransferEnvelope.decrypt(notABackup, "pw")
            }
        assertEquals("Not an Amethyst transfer file", error.message)
    }

    @Test
    fun rejectsAFileShorterThanTheHeader() {
        val error =
            assertFailsWith<AccountTransferEnvelope.InvalidTransferFile> {
                AccountTransferEnvelope.decrypt("too small".encodeToByteArray(), "pw")
            }
        assertEquals("Not an Amethyst transfer file: too short", error.message)
    }

    @Test
    fun rejectsATruncatedFile() {
        val encrypted = AccountTransferEnvelope.encrypt(bundle, "pw", testLogN)

        assertFailsWith<AccountTransferEnvelope.InvalidTransferFile> {
            AccountTransferEnvelope.decrypt(encrypted.copyOfRange(0, 20), "pw")
        }
    }

    @Test
    fun rejectsANewerFormatVersion() {
        val encrypted = AccountTransferEnvelope.encrypt(bundle, "pw", testLogN)
        encrypted[8] = (AccountTransferEnvelope.VERSION + 1).toByte()

        val error =
            assertFailsWith<AccountTransferEnvelope.InvalidTransferFile> {
                AccountTransferEnvelope.decrypt(encrypted, "pw")
            }
        assertEquals("This file was written by a newer version of Amethyst", error.message)
    }

    @Test
    fun roundTripsAnEmptyBundle() {
        val empty = AccountTransferBundle(createdAt = 1)
        val encrypted = AccountTransferEnvelope.encrypt(empty, "pw", testLogN)

        assertEquals(empty, AccountTransferEnvelope.decrypt(encrypted, "pw"))
    }

    @Test
    fun roundTripsAnAccountWithoutASecretKey() {
        val settingsOnly =
            AccountTransferBundle(
                createdAt = 1,
                accounts = listOf(AccountTransferEntry(npub = "npub1x", externalSignerPackageName = "com.example.signer")),
            )
        val encrypted = AccountTransferEnvelope.encrypt(settingsOnly, "pw", testLogN)
        val decrypted = AccountTransferEnvelope.decrypt(encrypted, "pw")

        assertNull(decrypted.accounts.single().privKeyHex)
        assertEquals("com.example.signer", decrypted.accounts.single().externalSignerPackageName)
    }

    @Test
    fun anEmptyPasswordStillEncrypts() {
        // The UI requires a password, but nothing in the format depends on that,
        // and a format that silently produced plaintext here would be worse.
        val encrypted = AccountTransferEnvelope.encrypt(bundle, "", testLogN)

        assertEquals(bundle, AccountTransferEnvelope.decrypt(encrypted, ""))
        assertFailsWith<AccountTransferEnvelope.InvalidTransferFile> {
            AccountTransferEnvelope.decrypt(encrypted, "x")
        }
    }

    @Test
    fun twoExportsOfTheSameBundleDiffer() {
        // Fresh salt and nonce per export: identical files would leak that the
        // settings did not change between two backups.
        val first = AccountTransferEnvelope.encrypt(bundle, "pw", testLogN)
        val second = AccountTransferEnvelope.encrypt(bundle, "pw", testLogN)

        assertFalse(first.contentEquals(second))
    }

    @Test
    fun excludesDeviceBoundKeys() {
        assertFalse(AccountTransferKeys.isTransferable("nostr_privkey"))
        assertFalse(AccountTransferKeys.isTransferable("nip46BunkerSecret"))
        assertFalse(AccountTransferKeys.isTransferable("nip46TransportKey"))
        assertFalse(AccountTransferKeys.isTransferable("nip46SeenRequestIds"))

        // The user-facing toggle travels; only the device identity behind it does not.
        assertTrue(AccountTransferKeys.isTransferable("nip46SignerEnabled"))
        assertTrue(AccountTransferKeys.isTransferable("nwcWallets"))
    }
}

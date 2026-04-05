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
package com.vitorpamplona.amethyst.cli.engine

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ported from whitenoise-rs src/cli/account.rs tests.
 * Tests account resolution, identity creation, login/logout lifecycle.
 */
class AccountStoreTest {
    private lateinit var dataDir: File
    private lateinit var store: AccountStore

    @BeforeTest
    fun setup() {
        dataDir = File(System.getProperty("java.io.tmpdir"), "marmot-test-${System.nanoTime()}")
        dataDir.mkdirs()
        store = AccountStore(dataDir)
    }

    @AfterTest
    fun cleanup() {
        dataDir.deleteRecursively()
    }

    // -- Ported from whitenoise-rs: resolve_single_account --
    @Test
    fun resolveSingleAccount() {
        val info = store.createIdentity()
        val resolved = store.resolveAccount(null)
        assertNotNull(resolved)
        assertEquals(info.pubKeyHex, resolved.pubKeyHex)
    }

    // -- Ported from whitenoise-rs: resolve_no_accounts_errors --
    @Test
    fun resolveNoAccountsReturnsNull() {
        val resolved = store.resolveAccount(null)
        assertNull(resolved, "should return null when no accounts exist")
    }

    // -- Ported from whitenoise-rs: resolve_multiple_accounts_errors --
    // In our implementation, resolveAccount falls back to default account
    // rather than erroring, so we test that the default is used correctly.
    @Test
    fun resolveMultipleAccountsUsesDefault() {
        val info1 = store.createIdentity()
        val info2 = store.createIdentity()

        // The second created identity becomes the default
        val resolved = store.resolveAccount(null)
        assertNotNull(resolved)
        assertEquals(info2.pubKeyHex, resolved.pubKeyHex)
    }

    // -- Additional tests matching whitenoise-rs patterns --

    @Test
    fun createIdentityGeneratesUniqueKeys() {
        val info1 = store.createIdentity()
        val info2 = store.createIdentity()

        assertNotEquals(info1.pubKeyHex, info2.pubKeyHex, "two identities created the same pubkey")
        assertNotEquals(info1.privKeyHex, info2.privKeyHex, "two identities created the same privkey")
    }

    @Test
    fun createIdentityReturnsValidBech32() {
        val info = store.createIdentity()

        assertTrue(info.npub.startsWith("npub1"), "npub should start with npub1")
        assertTrue(info.nsec.startsWith("nsec1"), "nsec should start with nsec1")
        assertEquals(64, info.pubKeyHex.length, "pubkey hex should be 64 chars")
        assertEquals(64, info.privKeyHex.length, "privkey hex should be 64 chars")
    }

    @Test
    fun loginWithNsec() {
        val created = store.createIdentity()
        val nsec = created.nsec

        // Create a fresh store to simulate a new session
        val freshDir = File(System.getProperty("java.io.tmpdir"), "marmot-test-login-${System.nanoTime()}")
        freshDir.mkdirs()
        try {
            val freshStore = AccountStore(freshDir)
            val loggedIn = freshStore.login(nsec, emptyList())

            assertEquals(created.pubKeyHex, loggedIn.pubKeyHex, "login should recover same pubkey from nsec")
            assertEquals(created.npub, loggedIn.npub)
        } finally {
            freshDir.deleteRecursively()
        }
    }

    @Test
    fun loginWithHexPrivkey() {
        val created = store.createIdentity()
        val hexKey = created.privKeyHex

        val freshDir = File(System.getProperty("java.io.tmpdir"), "marmot-test-hex-${System.nanoTime()}")
        freshDir.mkdirs()
        try {
            val freshStore = AccountStore(freshDir)
            val loggedIn = freshStore.login(hexKey, emptyList())

            assertEquals(created.pubKeyHex, loggedIn.pubKeyHex, "login should recover same pubkey from hex privkey")
        } finally {
            freshDir.deleteRecursively()
        }
    }

    @Test
    fun loginWithCustomRelays() {
        val created = store.createIdentity()
        val relays = listOf("wss://custom.relay.example")

        val freshDir = File(System.getProperty("java.io.tmpdir"), "marmot-test-relays-${System.nanoTime()}")
        freshDir.mkdirs()
        try {
            val freshStore = AccountStore(freshDir)
            val loggedIn = freshStore.login(created.nsec, relays)

            assertEquals(relays, loggedIn.relays, "custom relays should be stored")
        } finally {
            freshDir.deleteRecursively()
        }
    }

    @Test
    fun logoutRemovesAccount() {
        val info = store.createIdentity()
        val accounts = store.listAccounts()
        assertEquals(1, accounts.size)

        val result = store.logout(info.pubKeyHex)
        assertTrue(result, "logout should return true for existing account")

        val remaining = store.listAccounts()
        assertEquals(0, remaining.size, "account should be removed after logout")
    }

    @Test
    fun logoutNonexistentReturnsFalse() {
        val result = store.logout("0000000000000000000000000000000000000000000000000000000000000000")
        assertTrue(!result, "logout should return false for nonexistent account")
    }

    @Test
    fun listAccountsShowsAll() {
        store.createIdentity()
        store.createIdentity()
        store.createIdentity()

        val accounts = store.listAccounts()
        assertEquals(3, accounts.size, "should list all created accounts")
    }

    @Test
    fun resolveAccountByPubkeyFlag() {
        val info1 = store.createIdentity()
        val info2 = store.createIdentity()

        // Explicitly resolve the first account (not the default)
        val resolved = store.resolveAccount(info1.pubKeyHex)
        assertNotNull(resolved)
        assertEquals(info1.pubKeyHex, resolved.pubKeyHex)
    }

    @Test
    fun resolveAccountByNpub() {
        val info = store.createIdentity()

        val resolved = store.resolveAccount(info.npub)
        assertNotNull(resolved)
        assertEquals(info.pubKeyHex, resolved.pubKeyHex)
    }

    @Test
    fun defaultAccountUpdatesOnLogout() {
        val info1 = store.createIdentity()
        val info2 = store.createIdentity()

        // info2 is the default
        assertEquals(info2.pubKeyHex, store.getDefaultAccount())

        // Logout the default
        store.logout(info2.pubKeyHex)

        // Default should switch to remaining account
        assertEquals(info1.pubKeyHex, store.getDefaultAccount())
    }

    @Test
    fun signerProducesValidSigner() {
        val info = store.createIdentity()
        val signer = store.signerFor(info)

        assertEquals(info.pubKeyHex, signer.pubKey)
    }

    @Test
    fun relayManagement() {
        val info = store.createIdentity()

        // Default relays are set on creation
        val defaultRelays = store.getRelays(info.pubKeyHex)
        assertTrue(defaultRelays.isNotEmpty(), "should have default relays")

        // Set custom relays
        val custom = listOf("wss://relay1.example", "wss://relay2.example")
        store.setRelays(info.pubKeyHex, custom)

        val retrieved = store.getRelays(info.pubKeyHex)
        assertEquals(custom, retrieved)
    }
}

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
import kotlin.test.assertTrue

/**
 * Ported from whitenoise-rs tests/cli_e2e.rs.
 * Tests the MarmotEngine directly (no relay connections needed).
 *
 * These correspond to the whitenoise E2E tests:
 * - two_daemons_create_independent_identities
 * - account_profile_and_export
 * - group_metadata_and_membership
 * - cross_daemon_group_messaging (partial - no relay needed for local encrypt/decrypt)
 */
class MarmotEngineTest {
    private lateinit var dataDir: File
    private lateinit var accountStore: AccountStore

    @BeforeTest
    fun setup() {
        dataDir = File(System.getProperty("java.io.tmpdir"), "marmot-engine-test-${System.nanoTime()}")
        dataDir.mkdirs()
        accountStore = AccountStore(dataDir)
    }

    @AfterTest
    fun cleanup() {
        dataDir.deleteRecursively()
    }

    private fun createEngine(account: AccountInfo): MarmotEngine =
        MarmotEngine(
            account = account,
            signer = accountStore.signerFor(account),
            dataDir = dataDir,
        )

    // -- Ported from whitenoise-rs: two_daemons_create_independent_identities --
    @Test
    fun twoEnginesCreateIndependentIdentities() {
        val info1 = accountStore.createIdentity()
        val info2 = accountStore.createIdentity()

        assertNotEquals(info1.pubKeyHex, info2.pubKeyHex, "two identities created the same pubkey")
        assertTrue(info1.npub.startsWith("npub1"))
        assertTrue(info2.npub.startsWith("npub1"))
    }

    // -- Ported from whitenoise-rs: account_profile_and_export --
    @Test
    fun accountExportNsec() {
        val info = accountStore.createIdentity()

        assertTrue(info.nsec.startsWith("nsec1"), "expected nsec1 prefix, got: ${info.nsec}")

        // Verify we can load the account back
        val loaded = accountStore.loadAccount(info.pubKeyHex)
        assertNotNull(loaded)
        assertEquals(info.nsec, loaded.nsec)
        assertEquals(info.pubKeyHex, loaded.pubKeyHex)
    }

    // -- Ported from whitenoise-rs: group_metadata_and_membership --
    @Test
    fun groupCreationAndMetadata() {
        val alice = accountStore.createIdentity()
        val engine = createEngine(alice)

        val group =
            engine.createGroup(
                name = "Metadata Test",
                description = "A test group",
                relays = listOf("wss://relay.example.com"),
            )

        assertNotNull(group.nostrGroupId)
        assertEquals(64, group.nostrGroupId.length, "group ID should be 64-char hex")
        assertEquals("Metadata Test", group.name)
        assertEquals("A test group", group.description)
        assertEquals(1, group.memberCount, "new group should have 1 member (creator)")
        assertTrue(group.adminPubkeys.contains(alice.pubKeyHex), "creator should be admin")
    }

    @Test
    fun groupMembersListsCreator() {
        val alice = accountStore.createIdentity()
        val engine = createEngine(alice)

        val group = engine.createGroup("Test", "", emptyList())
        val members = engine.groupMembers(group.nostrGroupId)

        assertNotNull(members)
        assertEquals(1, members.size, "should have exactly 1 member")
    }

    @Test
    fun groupShowReturnsDetails() {
        val alice = accountStore.createIdentity()
        val engine = createEngine(alice)

        val created = engine.createGroup("Show Test", "desc", listOf("wss://relay.example"))
        val shown = engine.showGroup(created.nostrGroupId)

        assertNotNull(shown)
        assertEquals(created.nostrGroupId, shown.nostrGroupId)
    }

    @Test
    fun showNonexistentGroupReturnsNull() {
        val alice = accountStore.createIdentity()
        val engine = createEngine(alice)

        val result = engine.showGroup("0000000000000000000000000000000000000000000000000000000000000000")
        assertEquals(null, result)
    }

    // -- Ported from whitenoise-rs: key package generation --
    @Test
    fun generateKeyPackageProducesValidOutput() {
        val alice = accountStore.createIdentity()
        val engine = createEngine(alice)

        val kpInfo = engine.generateKeyPackage()

        assertTrue(kpInfo.base64.isNotEmpty(), "KeyPackage base64 should not be empty")
        assertEquals(64, kpInfo.ref.length, "KeyPackage ref should be 64-char hex")
        assertEquals("0x0001", kpInfo.ciphersuite)
    }

    @Test
    fun buildKeyPackageEventProducesSignedEvent() {
        val alice = accountStore.createIdentity()
        val engine = createEngine(alice)

        val kpInfo = engine.generateKeyPackage()
        val event = engine.buildKeyPackageEvent(kpInfo)

        assertEquals(30443, event.kind, "KeyPackage event should be kind 30443")
        assertEquals(alice.pubKeyHex, event.pubKey)
        assertTrue(event.id.isNotEmpty(), "event should have an ID")
        assertTrue(event.sig.isNotEmpty(), "event should have a signature")
    }

    // -- Ported from whitenoise-rs: multiple groups --
    @Test
    fun multipleGroupsAreListed() {
        val alice = accountStore.createIdentity()
        val engine = createEngine(alice)

        engine.createGroup("Group 1", "", emptyList())
        engine.createGroup("Group 2", "", emptyList())
        engine.createGroup("Group 3", "", emptyList())

        val groups = engine.listGroups()
        assertEquals(3, groups.size, "should list all 3 groups")
    }

    // -- Ported from whitenoise-rs: second_identity_does_not_break_first_account --
    @Test
    fun multipleIdentitiesHaveIsolatedState() {
        val alice = accountStore.createIdentity()
        val bob = accountStore.createIdentity()

        val aliceEngine = createEngine(alice)
        val bobEngine = createEngine(bob)

        aliceEngine.createGroup("Alice's Group", "", emptyList())
        bobEngine.createGroup("Bob's Group", "", emptyList())

        val aliceGroups = aliceEngine.listGroups()
        val bobGroups = bobEngine.listGroups()

        assertEquals(1, aliceGroups.size)
        assertEquals(1, bobGroups.size)
        assertNotEquals(
            aliceGroups[0].nostrGroupId,
            bobGroups[0].nostrGroupId,
            "groups should have different IDs",
        )
    }
}

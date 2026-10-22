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

import com.vitorpamplona.amethyst.cli.secrets.BunkerFile
import com.vitorpamplona.amethyst.cli.secrets.IdentityFile
import com.vitorpamplona.amethyst.cli.secrets.IdentitySecret
import com.vitorpamplona.amethyst.cli.stores.StoredCommunity
import com.vitorpamplona.amethyst.cli.stores.StoredHeldRoot
import com.vitorpamplona.amethyst.cli.stores.StoredInviteInbox
import com.vitorpamplona.amethyst.cli.stores.StoredPendingRefounding
import com.vitorpamplona.amethyst.cli.stores.StoredPrivateChannel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * amy's files under `~/.amy` kept their format across the move from Jackson to kotlinx: every
 * expected string below is exactly what Jackson's `jacksonObjectMapper()` wrote for the same
 * value, and each must both come out of the new codec and read back into that value.
 */
class CliFileFormatTest {
    private val community =
        StoredCommunity(
            name = "n",
            communityId = "c",
            owner = "o",
            ownerSalt = "s",
            root = "r",
            rootEpoch = 2,
            controlPk = "cp",
            controlRoot = "cr",
            generalChannelId = "g",
            relays = listOf("wss://a"),
            heldRoots = listOf(StoredHeldRoot(1, "r1", "cp1", "cr1")),
            inviteRef = "naddr#f",
            privateChannels = listOf(StoredPrivateChannel("ch", "k", 3, "secret")),
            channelCuts = mapOf("ch" to 2L),
            pendingChannelRotations = mapOf("ch:4:pc" to "key"),
            pendingRefounding = StoredPendingRefounding(3, "pc", "nr", "ncr"),
            addedAt = 99,
        )

    @Test
    fun identityFiles() {
        val cases =
            listOf(
                IdentityFile("aa", "npub1x", IdentitySecret.Keychain("macos", "amy", "acct")) to
                    """{"pubKeyHex":"aa","npub":"npub1x","secret":{"type":"keychain","backend":"macos","service":"amy","account":"acct"}}""",
                IdentityFile("aa", "npub1x", IdentitySecret.Ncryptsec("ncryptsec1q")) to
                    """{"pubKeyHex":"aa","npub":"npub1x","secret":{"type":"ncryptsec","ncryptsec":"ncryptsec1q"}}""",
                IdentityFile("aa", "npub1x", IdentitySecret.Plaintext("ff")) to
                    """{"pubKeyHex":"aa","npub":"npub1x","secret":{"type":"plaintext","privKeyHex":"ff"}}""",
                IdentityFile("aa", "npub1x", null, privKeyHex = "ff", nsec = "nsec1") to
                    """{"pubKeyHex":"aa","npub":"npub1x","privKeyHex":"ff","nsec":"nsec1"}""",
                IdentityFile("aa", "npub1x", IdentitySecret.Plaintext("ff"), bunker = BunkerFile("bb", listOf("wss://r"), "sec", 17)) to
                    """{"pubKeyHex":"aa","npub":"npub1x","secret":{"type":"plaintext","privKeyHex":"ff"},"bunker":{"remotePubkey":"bb","relays":["wss://r"],"connectSecret":"sec","relaysCheckedAt":17}}""",
                IdentityFile("aa", "npub1x", IdentitySecret.Plaintext("ff"), bunker = BunkerFile("bb", emptyList())) to
                    """{"pubKeyHex":"aa","npub":"npub1x","secret":{"type":"plaintext","privKeyHex":"ff"},"bunker":{"remotePubkey":"bb","relays":[]}}""",
            )
        for ((value, jackson) in cases) {
            assertEquals(jackson, CliJson.sparseFiles.encodeToString(value))
            assertEquals(value, CliJson.sparseFiles.decodeFromString<IdentityFile>(jackson))
        }
    }

    @Test
    fun runState() {
        assertEquals("""{"giftWrapSince":5,"groupSince":{"g1":7}}""", CliJson.files.encodeToString(RunState(5, mutableMapOf("g1" to 7L))))
        assertEquals("""{"giftWrapSince":null,"groupSince":{}}""", CliJson.files.encodeToString(RunState()))
        assertEquals(RunState(5, mutableMapOf("g1" to 7L)), CliJson.files.decodeFromString<RunState>("""{"giftWrapSince":5,"groupSince":{"g1":7}}"""))
        assertEquals(RunState(), CliJson.files.decodeFromString<RunState>("""{"giftWrapSince":null,"groupSince":{}}"""))
    }

    @Test
    fun concordStores() {
        val jackson =
            """[{"name":"n","communityId":"c","owner":"o","ownerSalt":"s","root":"r","rootEpoch":2,"controlPk":"cp","controlRoot":"cr","generalChannelId":"g",""" +
                """"relays":["wss://a"],"heldRoots":[{"epoch":1,"root":"r1","controlPk":"cp1","controlRoot":"cr1"}],"inviteRef":"naddr#f",""" +
                """"privateChannels":[{"channelId":"ch","key":"k","epoch":3,"name":"secret"}],"channelCuts":{"ch":2},"pendingChannelRotations":{"ch:4:pc":"key"},""" +
                """"pendingRefounding":{"rootEpoch":3,"prevCommit":"pc","newRoot":"nr","newControlRoot":"ncr"},"addedAt":99},""" +
                """{"name":"","communityId":"","owner":"","ownerSalt":"","root":"","rootEpoch":0,"controlPk":"","controlRoot":"","generalChannelId":"","relays":[],""" +
                """"heldRoots":[],"inviteRef":"","privateChannels":[],"channelCuts":{},"pendingChannelRotations":{},"pendingRefounding":null,"addedAt":0}]"""
        val value = listOf(community, StoredCommunity())
        assertEquals(jackson, CliJson.files.encodeToString(value))
        assertEquals(value, CliJson.files.decodeFromString<List<StoredCommunity>>(jackson))

        assertEquals("""{"declined":["a","b"]}""", CliJson.files.encodeToString(StoredInviteInbox(listOf("a", "b"))))
        assertEquals(StoredInviteInbox(listOf("a", "b")), CliJson.files.decodeFromString<StoredInviteInbox>("""{"declined":["a","b"]}"""))
    }

    @Test
    fun operatorKeysAndAliases() {
        val full = OperatorKeys.Config("mm", IdentitySecret.Keychain("b", "s", "a"), listOf("wss://x"), mutableMapOf("p1" to OperatorKeys.ProviderRecord("pp")))
        val fullJson = """{"masterPubKey":"mm","master":{"type":"keychain","backend":"b","service":"s","account":"a"},"relays":["wss://x"],"providers":{"p1":{"providerPubKey":"pp"}}}"""
        assertEquals(fullJson, CliJson.files.encodeToString(full))
        assertEquals(full, CliJson.files.decodeFromString<OperatorKeys.Config>(fullJson))
        val emptyJson = """{"masterPubKey":"","master":null,"relays":[],"providers":{}}"""
        assertEquals(emptyJson, CliJson.files.encodeToString(OperatorKeys.Config()))
        assertEquals(OperatorKeys.Config(), CliJson.files.decodeFromString<OperatorKeys.Config>(emptyJson))

        assertEquals("""{"alice":"npub1a"}""", CliJson.files.encodeToString<Map<String, String>>(mapOf("alice" to "npub1a")))
    }

    @Test
    fun unknownKeysAreSkipped() {
        val read = CliJson.sparseFiles.decodeFromString<IdentityFile>("""{"pubKeyHex":"aa","npub":"npub1x","addedLater":[1,2],"secret":{"type":"plaintext","privKeyHex":"ff","x":1}}""")
        assertEquals(IdentityFile("aa", "npub1x", IdentitySecret.Plaintext("ff")), read)
    }
}

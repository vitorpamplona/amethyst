/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip57Zaps

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.jackson.EventMapper
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.toHexKey
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapEncryption.Companion.createEncryptionPrivateKey
import com.vitorpamplona.quartz.nip59GiftWraps.wait1SecondForResult
import com.vitorpamplona.quartz.utils.Hex
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivateZapTests {
    @Test
    fun testPollZap() {
        val poll =
            EventMapper.fromJson(
                """{
  "content": "New poll \n\n #zappoll",
  "created_at": 1682440713,
  "id": "16291ba452bb0786a4bf5c278d38de73c96b58c056ed75c5ea466b0795197288",
  "kind": 6969,
  "pubkey": "f8ff11c7a7d3478355d3b4d174e5a473797a906ea4aa61aa9b6bc0652c1ea17a",
  "sig": "ac05fa4004c3f7c42913c87b11bf9714bb61a3f0940863a6b9ff0f8105b399add72dbc09bf944c79b9a72ef009ec6905adedbd2c4c8fb3d2f57007bad8fcb279",
  "tags": [
    [
      "poll_option",
      "0",
      "Test 1"
    ],
    [
      "poll_option",
      "1",
      "Test 2"
    ],
    [
      "value_maximum",
      "null"
    ],
    [
      "value_minimum",
      "null"
    ],
    [
      "consensus_threshold",
      "null"
    ],
    [
      "closed_at",
      "null"
    ]
  ],
  "seenOn": [
    "wss://relay.damus.io/"
  ]
}
""",
            )

        val loggedIn =
            NostrSignerInternal(
                KeyPair(
                    Hex.decode("e8e7197ccc53c9ed4cf9b1c8dce085475fa1ffdd71f2c14e44fe23d0bdf77598"),
                ),
            )

        var resultPrivateZap: Event? = null

        wait1SecondForResult { onDone ->
            LnZapRequestEvent.create(
                originalNote = poll,
                relays = setOf("wss://relay.damus.io/"),
                signer = loggedIn,
                pollOption = 0,
                message = "",
                zapType = LnZapEvent.ZapType.PRIVATE,
                toUserPubHex = null,
            ) { privateZapRequest ->
                val recepientPK = privateZapRequest.zappedAuthor().firstOrNull()
                val recepientPost = privateZapRequest.zappedPost().firstOrNull()

                if (recepientPK != null && recepientPost != null) {
                    val privateKey =
                        createEncryptionPrivateKey(
                            loggedIn.keyPair.privKey!!.toHexKey(),
                            recepientPost,
                            privateZapRequest.createdAt,
                        )
                    val decodedPrivateZap = privateZapRequest.getPrivateZapEvent(privateKey, recepientPK)

                    println(decodedPrivateZap?.toJson())

                    resultPrivateZap = decodedPrivateZap

                    onDone()
                } else {
                    fail("Should not be null")
                }
            }
        }

        assertNotNull(resultPrivateZap)
    }

    @Test
    fun testKind1PrivateZap() {
        val textNote =
            Event.fromJson(
                """{
    "content": "Testing copied author. \n\nnostr:npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z",
    "created_at": 1682369982,
    "id": "c757e1371d715c711ec9ef9740a3df6475d64b3d0af45ffcbfca08d273baf1c1",
    "kind": 1,
    "pubkey": "f8ff11c7a7d3478355d3b4d174e5a473797a906ea4aa61aa9b6bc0652c1ea17a",
    "sig": "1fb5b6fd980f4c2ef058d5f4f7b166c0e5fb21eff26fe9cacd87a9aa4feb344485841ebcc26a233bf8d6ea0a66acf0db2bfdb11ad1cb04bcea4cfa3e78c3eaf1",
    "tags": [
    [
        "p",
        "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    ]
    ],
    "seenOn": [
    "wss://relay.damus.io/"
    ]
}
""",
            )

        val loggedIn =
            NostrSignerInternal(
                KeyPair(Hex.decode("e8e7197ccc53c9ed4cf9b1c8dce085475fa1ffdd71f2c14e44fe23d0bdf77598")),
            )

        var resultPrivateZap: Event? = null

        wait1SecondForResult { onDone ->
            LnZapRequestEvent.create(
                originalNote = textNote,
                relays = setOf("wss://relay.damus.io/", "wss://relay.damus2.io/", "wss://relay.damus3.io/"),
                signer = loggedIn,
                pollOption = null,
                message = "test",
                zapType = LnZapEvent.ZapType.PRIVATE,
                toUserPubHex = null,
            ) { privateZapRequest ->
                val recepientPK = privateZapRequest.zappedAuthor().firstOrNull()
                val recepientPost = privateZapRequest.zappedPost().firstOrNull()

                if (recepientPK != null && recepientPost != null) {
                    val privateKey =
                        createEncryptionPrivateKey(
                            loggedIn.keyPair.privKey!!.toHexKey(),
                            recepientPost,
                            privateZapRequest.createdAt,
                        )
                    val decodedPrivateZap = privateZapRequest.getPrivateZapEvent(privateKey, recepientPK)

                    println(decodedPrivateZap?.toJson())

                    resultPrivateZap = decodedPrivateZap

                    onDone()
                } else {
                    fail("Should not be null")
                }
            }
        }

        assertNotNull(resultPrivateZap)
    }
}

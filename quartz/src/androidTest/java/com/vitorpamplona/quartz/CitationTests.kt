package com.vitorpamplona.quartz

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.TextNoteEvent
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CitationTests {
    val json = """
    {
  "content": "Astral:\n\nhttps://void.cat/d/A5Fba5B1bcxwEmeyoD9nBs.webp\n\nIris:\n\nhttps://void.cat/d/44hTcVvhRps6xYYs99QsqA.webp\n\nSnort:\n\nhttps://void.cat/d/4nJD5TRePuQChM5tzteYbU.webp\n\nAmethyst agrees with Astral which I suspect are both wrong. nostr:npub13sx6fp3pxq5rl70x0kyfmunyzaa9pzt5utltjm0p8xqyafndv95q3saapa nostr:npub1v0lxxxxutpvrelsksy8cdhgfux9l6a42hsj2qzquu2zk7vc9qnkszrqj49 nostr:npub1g53mukxnjkcmr94fhryzkqutdz2ukq4ks0gvy5af25rgmwsl4ngq43drvk nostr:npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z ",
  "created_at": 1683596206,
  "id": "98b574c3527f0ffb30b7271084e3f07480733c7289f8de424d29eae82e36c758",
  "kind": 1,
  "pubkey": "46fcbe3065eaf1ae7811465924e48923363ff3f526bd6f73d7c184b16bd8ce4d",
  "sig": "4aa5264965018fa12a326686ad3d3bd8beae3218dcc83689b19ca1e6baeb791531943c15363aa6707c7c0c8b2d601deca1f20c32078b2872d356cdca03b04cce",
  "tags": [
    [
      "e",
      "27ac621d7dc4a932e1a79f984308e7d20656dd6fddb2ce9cdfcb6a67b9a7bcc3",
      "",
      "root"
    ],
    [
      "e",
      "be7245af96210a0dd048cab4ad38e52dbd6c09a53ea21a7edb6be8898e5727cc",
      "",
      "reply"
    ],
    [
      "p",
      "22aa81510ee63fe2b16cae16e0921f78e9ba9882e2868e7e63ad6d08ae9b5954"
    ],
    [
      "p",
      "22aa81510ee63fe2b16cae16e0921f78e9ba9882e2868e7e63ad6d08ae9b5954"
    ],
    [
      "p",
      "3f770d65d3a764a9c5cb503ae123e62ec7598ad035d836e2a810f3877a745b24"
    ],
    [
      "p",
      "ec4d241c334311b3a304433ee3442be29d0e88e7ec19b85edf2bba29b93565e2"
    ],
    [
      "p",
      "0fe0b18b4dbf0e0aa40fcd47209b2a49b3431fc453b460efcf45ca0bd16bd6ac"
    ],
    [
      "p",
      "8c0da4862130283ff9e67d889df264177a508974e2feb96de139804ea66d6168"
    ],
    [
      "p",
      "63fe6318dc58583cfe16810f86dd09e18bfd76aabc24a0081ce2856f330504ed"
    ],
    [
      "p",
      "4523be58d395b1b196a9b8c82b038b6895cb02b683d0c253a955068dba1facd0"
    ],
    [
      "p",
      "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    ]
  ],
  "seenOn": [
    "wss://nostr.wine/"
  ]
}
"""

    @Test
    fun parseEvent() {
        val event = Event.fromJson(json) as TextNoteEvent

        val expectedCitations = setOf(
            "8c0da4862130283ff9e67d889df264177a508974e2feb96de139804ea66d6168",
            "63fe6318dc58583cfe16810f86dd09e18bfd76aabc24a0081ce2856f330504ed",
            "4523be58d395b1b196a9b8c82b038b6895cb02b683d0c253a955068dba1facd0",
            "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
        )

        assertEquals(expectedCitations, event.citedUsers())
    }
}

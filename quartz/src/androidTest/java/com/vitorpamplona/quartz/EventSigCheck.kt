package com.vitorpamplona.quartz

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.events.Event
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventSigCheck {

    val payload1 = "[\"EVENT\",\"40b9\",{\"id\":\"48a72b485d38338627ec9d427583551f9af4f016c739b8ec0d6313540a8b12cf\",\"kind\":1,\"pubkey\":\"3d842afecd5e293f28b6627933704a3fb8ce153aa91d790ab11f6a752d44a42d\",\"created_at\":1677940007,\"content\":\"I got asked about follower count again today. Why does my follower count go down when I delete public relays (in our list) and replace them with filter.nostr.wine? \\n\\nI’ll give you one final explanation to rule them all. First, let’s go over how clients calculate your follower count.\\n\\n1. Your client sends a request to all your connected relays asking for accounts who follow you\\n2. Relays answer back with the events requested\\n3. The client aggregates the event total and displays it\\n\\nEach relay has a set limit on how many stored events it will return per request. For some relays it’s 500, others 1000, some as high as 5000. Let’s say for simplicity that all your public relays use 500 as their limit. If you ask 10 relays for your followers the max possible answer you can get is 5000. That won’t change if you have 20,000 followers or 100,000. You may get back a “different” 5000 each time, but you’ll still cap out at 5000 because that is the most events your client will receive.\u2028\u2028Our limit on filter.nostr.wine is 2000 events. If you replace 10 public relays with only filter.nostr.wine, the MOST followers you will ever get back from our filter relay is 2000. That doesn’t mean you only have 2000 followers or that your reach is reduced in any way.\\n\\nAs long as you are writing to and reading from the same public relays, neither your reach nor any content was lost. That concludes my TED talk. I hope you all have a fantastic day and weekend.\",\"tags\":[],\"sig\":\"dcaf8ab98bb9179017b35bd814092850d1062b26c263dff89fb1ae8c019a324139d1729012d9d05ff0a517f76b1117d869b2cc7d36bea8aa5f4b94c5e2548aa8\"}]"

    @Test
    fun testUnicode2028and2029ShouldNotBeEscaped() {
        val msg = Event.mapper.readTree(payload1)
        val event = Event.fromJson(msg[2])

        // Should pass
        event.checkSignature()
    }

}

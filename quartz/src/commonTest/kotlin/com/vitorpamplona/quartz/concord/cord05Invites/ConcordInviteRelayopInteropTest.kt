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
package com.vitorpamplona.quartz.concord.cord05Invites

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * A real, live relayop.xyz kind-33301 invite bundle (`vsk=6`) that decrypts fine but whose
 * plaintext diverges from quartz's model on two CORD-05 wire details: its `general` channel
 * omits the per-channel `key`, and `icon` is a bare public URL string rather than an
 * [com.vitorpamplona.quartz.concord.cord02Community.ImagePointer] object. Before the tolerant
 * decode both made [ConcordInviteBundle.parse] return null, so the invite reported
 * [InviteBundleStatus.Unreadable] instead of opening. It must now open [InviteBundleStatus.Live].
 *
 * This is a *distinct* interop failure from [ConcordInviteClassifyTest.realRelayopBundleIsUnreadable]
 * (a mis-posted `vsk=8` registry at the coordinate): here the sub-kind, token, and crypto are all
 * correct — only the JSON schema was too strict.
 */
class ConcordInviteRelayopInteropTest {
    private val bundleJson =
        """{"content": "AqQnNNWuadQ7DNwyb719TF5WVAfsswHSdf8LVvRACfliNlTf9dIHpCUhcJzkZAnyTkleBY/vzyJkcXuPfj5gsVtJXTeG45BhH/Mrz923t8lDEoFjuNqZwP0qD3Or/GPdeGh7TXYwlfZEHlefdovjbJ0J7unnY6bVIkiKdqhCgAiyhs1xQr8YN7g4B+1lvptP1J5fiXNZrdas+zSIvZTNpGTwC0/lPXUuxv8BwO7fjZDnaV9mqvcp2hVPmWZLwwZKpxB/uwcAQDGodIteukcF30idYTgWvon42H4ysbjU2/Hnd9C4I+gth4uT95wllENNtDLAX/FVJRMpOT5iYF6XDzSVhUS7cffKRjhevPrB4r9oTQobbhrkvC3Id2IZfL8R38j63ElEFyBeaI9wtZqN433eVawkNo3cjLAPlCiC3t+hUgFHpidgamyTKWrg+wT9KSVLB8OPtyAAFBpec3/c6T3Eaf/gS+TnbusmI3Ym0gWcsvd6WNPpEjA88YRgdIJWJuKpQlvV2O+OWroHdNnBSYe3qwcwsugJJYOXJqVaLc+cAA1L0KRLPcQa4poOZxCfNhB574oEHcn7ZyQK2yeZe4RfwTl6GesvwuGeHBLqKRjCPaBoynJoo8HOtqV1rRkibRCUDU5byTBS/s9TvufrLmu5DkY7ZsfkivgBTKAdNPlN/vgrV/pdkg8Fh/fMQ2qm+ET30eHCBAAZ6KAhKRaFZKgi2lTtCfzNxpz4cfxdRFIaXOLl1ajVuSNexP8kGg+NKLTkmvv2yDbjXmSlCohNoIuwD/kFG1yH3b3DbmFew6rVRSW15enyum1mcRFB6snWPigUN/RbKpLww5WGJCiABWgph1KJNct8Je5+T9TqkA8J0kAucjxD+IvYmdp1o/yMbjBrCcTZJh0x9CGhGA3G4hUX0HgmwJ2yq+noQa4qR0aoHYbgcecz2s5F7hnqCLrIPEOm8/+2qgekxkGbyirRQAF8mHeSJCP02sZV4VbWwdblWg0qZlsC5QbYRt5Zs5WfTzF3SdQkHpbIntX95JnlhxK5eW36+kr948FG8VNqFQAmc9UrqbGTaaHr78Cs5kgsjfc6FPdQw72VYrBBZlq0Y1XUY7rm+yBEF6BhTUOV83n0wmsStH34ZZnu4kt11pFp3GCk5TOxzNBlHI2SN6pn6qIqO9Mz9atwmq3q4nSKcyWVDSpIJAN/lvUWA/ja+l2gghiEGITRo9qjKZyrOYT7ZBwiS02TviGRvxj36dZ7SOwYQpwPnrw8dNNReQZaH2i6weEm", "created_at": 1784256213, "id": "73be636113d27d5e58b95e4f5db27a6f340cd56af9f44d1e480a948b4813a228", "kind": 33301, "pubkey": "2165cb2d743d28f26b02986eded6db864eca2d8441ec26e319ed7eafd62db4ec", "sig": "72475894de360d77c4fd924fde1e5109f3ce60953529f203fc19ab846c77e77f0fb6fac194414dfccc409bfa13ac895dfb075004fae3e6102c85dee6055d9e83", "tags": [["d", ""], ["vsk", "6"]]}"""

    // The 16-byte unlock token from the link's #fragment.
    private val token = "34656ab00097a2963e315b7e88bccd97".hexToByteArray()

    @Test
    fun realRelayopBundleWithStringIconAndKeylessChannelOpens() {
        val event = Event.fromJson(bundleJson)

        val invite = ConcordInviteBundle.parse(event, token)
        assertIs<CommunityInvite>(invite, "bundle must decrypt + deserialize despite string icon / keyless channel")
        assertEquals("3rd times a charm?", invite.name)
        assertEquals("3e6397fa824183b8b0eb2918eb7f038e26239cef376602dced9470b18352f296", invite.communityId)
        // The keyless public channel survived decoding with an empty grant key.
        assertEquals(1, invite.channels.size)
        assertEquals("general", invite.channels[0].name)
        assertEquals("", invite.channels[0].key)
        // The bare-URL icon was lifted into a plaintext (non-encrypted) ImagePointer.
        assertEquals("https://blossom.primal.net/a85a6b8f68cf602591b16846e1605f8034587b7220b44d7076d09f5e3bf5af71.jpg", invite.icon?.url)
        assertEquals(false, invite.icon?.isResolvable())

        val status = ConcordInviteBundle.classify(listOf(event), token)
        assertIs<InviteBundleStatus.Live>(status)
        assertEquals("3rd times a charm?", status.invite.name)
    }
}

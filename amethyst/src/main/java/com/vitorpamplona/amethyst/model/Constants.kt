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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

object Constants {
    val nos = RelayUrlNormalizer.normalize("wss://nos.lol")
    val mom = RelayUrlNormalizer.normalize("wss://nostr.mom")
    val primal = RelayUrlNormalizer.normalize("wss://relay.primal.net")
    val damus = RelayUrlNormalizer.normalize("wss://relay.damus.io")
    val wine = RelayUrlNormalizer.normalize("wss://nostr.wine")

    val where = RelayUrlNormalizer.normalize("wss://relay.noswhere.com")
    val elites = RelayUrlNormalizer.normalize("wss://nostrelites.org")

    val bitcoiner = RelayUrlNormalizer.normalize("wss://nostr.bitcoiner.social")
    val oxtr = RelayUrlNormalizer.normalize("wss://nostr.oxtr.dev")

    val nostoday = RelayUrlNormalizer.normalize("wss://search.nos.today")
    val antiprimal = RelayUrlNormalizer.normalize("wss://antiprimal.net")
    val ditto = RelayUrlNormalizer.normalize("wss://relay.ditto.pub")

    val auth = RelayUrlNormalizer.normalize("wss://auth.nostr1.com")
    val oxchat = RelayUrlNormalizer.normalize("wss://relay.0xchat.com")

    val purplepages = RelayUrlNormalizer.normalize("wss://purplepag.es")
    val coracle = RelayUrlNormalizer.normalize("wss://indexer.coracle.social")
    val userkinds = RelayUrlNormalizer.normalize("wss://user.kindpag.es")
    val yabu = RelayUrlNormalizer.normalize("wss://directory.yabu.me")
    val nostr1 = RelayUrlNormalizer.normalize("wss://profiles.nostr1.com")

    val bootstrapInbox = setOf(damus, primal, mom, nos, bitcoiner, oxtr, yabu)
    val eventFinderRelays = setOf(wine, damus, primal, mom, nos, bitcoiner, oxtr)
}

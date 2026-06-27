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
package com.vitorpamplona.amethyst.commons.browser

import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp

/**
 * The Nostr **web apps** offered as starting points in the browser launcher when the user hasn't
 * pinned or visited anything of their own yet. They are shown under a "Discover" section below Recent,
 * so they're discoverable without ever getting in the way of the user's own favorites/history.
 *
 * The list is drawn from the [nostrapps.com](https://nostrapps.com) directory — every entry that ships
 * a browser-openable web version — grouped here by what it does. Entries that are browser *extensions*
 * or signer-only tools (nos2x, Nostrame, …) are intentionally left out: they aren't something you open
 * in an in-app browser. URLs are the apps' own canonical domains; an entry is only included when its
 * URL could be confirmed, so a stale/guessed link never ships.
 *
 * These are plain [FavoriteApp.WebApp] entries (URL + label), so tapping one opens it like any other
 * web favorite and the user can star it to pin it for real. They are **not** persisted as favorites:
 * the list is hardcoded, device-local, and never becomes account state.
 *
 * No remote icon is set ([FavoriteApp.iconUrl] is null on purpose): the idle launcher must not phone
 * home to dozens of third-party servers before the user has chosen anything. Each site's real favicon
 * is captured the normal way once the user actually opens it; until then the entry shows the globe glyph.
 */
object DefaultWebClients {
    val list: List<FavoriteApp.WebApp> =
        buildList {
            // Social / microblogging clients
            webApp("Primal", "https://primal.net")
            webApp("Coracle", "https://coracle.social")
            webApp("Snort", "https://snort.social")
            webApp("noStrudel", "https://nostrudel.ninja")
            webApp("Iris", "https://iris.to")
            webApp("Nostter", "https://nostter.app")
            webApp("Jumble", "https://jumble.social")
            webApp("Nostria", "https://nostria.app")
            webApp("Nosotros", "https://nosotros.app")
            webApp("lumilumi", "https://lumilumi.app")
            webApp("Phoenix", "https://phoenix.social")
            webApp("Shosho", "https://shosho.live")
            webApp("ants", "https://ants.sh")
            webApp("YakiHonne", "https://yakihonne.com")
            webApp("Ditto", "https://ditto.pub")

            // Reading / long-form / feeds
            webApp("Habla", "https://habla.news")
            webApp("Highlighter", "https://highlighter.com")
            webApp("Boris", "https://readwithboris.com")
            webApp("Noflux", "https://noflux.nostr.technology")

            // Communities / chat
            webApp("Flotilla", "https://flotilla.social")
            webApp("Chachi", "https://chachi.chat")
            webApp("NostrChat", "https://www.nostrchat.io")

            // Media — video, photo, audio, files
            webApp("zap.stream", "https://zap.stream")
            webApp("Olas", "https://olas.app")
            webApp("Slidestr", "https://slidestr.net")
            webApp("Bouquet", "https://bouquet.slidestr.net")
            webApp("YakBak", "https://yakbak.app")
            webApp("Nests", "https://nostrnests.com")

            // Knowledge / wiki
            webApp("Wikifreedia", "https://wikifreedia.xyz")
            webApp("Wikistr", "https://wikistr.com")

            // Marketplace
            webApp("Shopstr", "https://shopstr.store")
            webApp("Plebeian Market", "https://plebeian.market")

            // Tools / utilities
            webApp("Emojito", "https://emojito.meme")
            webApp("Formstr", "https://formstr.app")
            webApp("Nostree", "https://nostree.me")
            webApp("Badges", "https://badges.page")
            webApp("Nstart", "https://nstart.me")
            webApp("alphaama", "https://alphaama.com")
            webApp("Treasures", "https://treasures.to")
            webApp("Yondar", "https://yondar.me")
            webApp("DTAN", "https://dtan.xyz")
            webApp("Nostrocket", "https://nostrocket.org")
            webApp("MAKIMONO", "https://makimono.lumilumi.app")
            webApp("Primal Studio", "https://studio.primal.net")
        }

    // addedAt is 0L: these are hardcoded suggestions, not user-added favorites, so they never need a
    // real "added" timestamp for ordering — the curated order in `list` is what matters.
    private fun MutableList<FavoriteApp.WebApp>.webApp(
        label: String,
        url: String,
    ) {
        add(FavoriteApp.WebApp(url = url, label = label, addedAt = 0L))
    }
}

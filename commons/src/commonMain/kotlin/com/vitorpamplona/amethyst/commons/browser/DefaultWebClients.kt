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
 * in an in-app browser. URLs are the apps' own canonical domains.
 *
 * Each [icon] is the app's **own** logo (the PNG/SVG declared in its `<link rel="apple-touch-icon">` /
 * `<link rel="icon">`), so the grid matches the favicon look of the Favorites/Recent rows without
 * routing through any third-party favicon service. Only PNG/SVG are used — Coil has no ICO decoder, so
 * an apps whose only icon is a `.ico` (and a few that couldn't be resolved) are left [icon]-less and
 * fall back to the globe glyph until their real favicon is captured the normal way on first visit.
 *
 * These are plain [FavoriteApp.WebApp] entries, so tapping one opens it like any other web favorite and
 * the user can star it to pin it for real. They are **not** persisted as favorites: the list is
 * hardcoded, device-local, and never becomes account state.
 */
object DefaultWebClients {
    val list: List<FavoriteApp.WebApp> =
        buildList {
            // Social / microblogging clients
            webApp("Primal", "https://primal.net", "https://primal.net/assets/apple-touch-icon-a536f430.png")
            webApp("Coracle", "https://coracle.social", "https://coracle.social/icons/apple-touch-icon-76x76.png")
            webApp("Snort", "https://snort.social", "https://snort.social/img/apple-touch-icon.png")
            webApp("noStrudel", "https://nostrudel.ninja", "https://nostrudel.ninja/apple-touch-icon.png")
            webApp("Iris", "https://iris.to", "https://iris.to/img/apple-touch-icon.png")
            webApp("Nostter", "https://nostter.app", "https://nostter.app/apple-touch-icon.png")
            webApp("Jumble", "https://jumble.social", "https://jumble.social/favicon.svg")
            webApp("Nostria", "https://nostria.app", "https://nostria.app/icons/icon-192x192-maskable.png")
            webApp("Nosotros", "https://nosotros.app", "https://nosotros.app/apple-touch-icon-144x144.png")
            webApp("lumilumi", "https://lumilumi.app", "https://lumilumi.app/apple-touch-icon-180x180.png")
            webApp("Phoenix", "https://phoenix.social", "https://phoenix.social/img/apple-touch-icon.png")
            webApp("Shosho", "https://shosho.live", "https://shosho.live/apple-touch-icon.png")
            webApp("ants", "https://ants.sh", "https://ants.sh/apple-touch-icon.png")
            webApp("YakiHonne", "https://yakihonne.com")
            webApp("Ditto", "https://ditto.pub", "https://ditto.pub/apple-touch-icon.png")
            webApp("x21", "https://x21.social", "https://x21.social/apple-touch-icon.png?v=4")
            webApp("Ghostr", "https://ghostr.org", "https://ghostr.org/favicon/apple-touch-icon.png")
            webApp("Mutable", "https://mutable.top", "https://mutable.top/mutable_logo.svg")

            // Reading / long-form / feeds
            webApp("Habla", "https://habla.news")
            webApp("Highlighter", "https://highlighter.com", "https://highlighter.com/apple-touch-icon-180x180.png")
            webApp("Boris", "https://readwithboris.com", "https://readwithboris.com/apple-touch-icon.png")
            webApp("Noflux", "https://noflux.nostr.technology")

            // Communities / chat
            webApp("Flotilla", "https://flotilla.social", "https://framerusercontent.com/images/8UjnVxSvRkmvY2lEYU5z8OMOw0M.png")
            webApp("Chachi", "https://chachi.chat")
            webApp("NostrChat", "https://www.nostrchat.io", "https://www.nostrchat.io/logo192.png")
            webApp("NymChat", "https://www.nymchat.com")

            // Media — video, photo, audio, podcasts, files
            webApp("zap.stream", "https://zap.stream", "https://zap.stream/logo.png")
            webApp("Divine Video", "https://divine.video", "https://divine.video/app_icon.png")
            webApp("Olas", "https://olas.app", "https://olas.app/favicon.png")
            webApp("Zappix", "https://zappix.app", "https://zappix.app/icon-192.png")
            webApp("Slidestr", "https://slidestr.net", "https://slidestr.net/slidestr.svg")
            webApp("Bouquet", "https://bouquet.slidestr.net", "https://bouquet.slidestr.net/bouquet.png")
            webApp("YakBak", "https://yakbak.app", "https://yakbak.app/yakbak-logo.png")
            webApp("ZapTrax", "https://zaptrax.app", "https://zaptrax.app/icon-192.png")
            webApp("Podstr", "https://podstr.org", "https://podstr.org/favicon.svg")
            webApp("Nests", "https://nostrnests.com", "https://nostrnests.com/apple-touch-icon.png")

            // Knowledge / wiki
            webApp("Wikifreedia", "https://wikifreedia.xyz", "https://wikifreedia.xyz/favicon.svg")
            webApp("Wikistr", "https://wikistr.com", "https://wikistr.com/favicon.png")

            // Marketplace / food
            webApp("Shopstr", "https://shopstr.store")
            webApp("Plebeian Market", "https://plebeian.market", "https://plebeian.market/logo-st5zpap9.svg")
            webApp("Zap Cooking", "https://zap.cooking", "https://zap.cooking/favicon.svg")

            // Tools / utilities
            webApp("Emojito", "https://emojito.meme")
            webApp("Formstr", "https://formstr.app", "https://formstr.app/logo192.png")
            webApp("Nostree", "https://nostree.me")
            webApp("Badges", "https://badges.page")
            webApp("Nstart", "https://nstart.me", "https://nstart.me/favicon.png")
            webApp("alphaama", "https://alphaama.com")
            webApp("Treasures", "https://treasures.to", "https://treasures.to/apple-touch-icon.png")
            webApp("Yondar", "https://yondar.me", "https://yondar.me/apple-touch-icon.png")
            webApp("DTAN", "https://dtan.xyz")
            webApp("Nostrocket", "https://nostrocket.org")
            webApp("Plektos", "https://plektos.app", "https://plektos.app/icon-180.png")
            webApp("Zaplytics", "https://zaplytics.app")
            webApp("Brainstorm", "https://brainstorm.world", "https://brainstorm.world/brainstorm.svg")
            webApp("MAKIMONO", "https://makimono.lumilumi.app", "https://makimono.lumilumi.app/favicon3.png")
            webApp("Primal Studio", "https://studio.primal.net")
            webApp("nostr.build", "https://nostr.build", "https://nostr.build/apple-touch-icon.png")
            webApp("nostrcheck", "https://nostrcheck.me", "https://nostrcheck.me/apple-touch-icon.png")
            webApp("Metadata", "https://metadata.nostr.com")

            // Games
            webApp("Plebs vs Zombies", "https://www.plebsvszombies.cc", "https://www.plebsvszombies.cc/favicon.svg")
            webApp("Blobbi", "https://www.blobbi.pet", "https://www.blobbi.pet/icons/apple-touch-icon.png")
        }

    // addedAt is 0L: these are hardcoded suggestions, not user-added favorites, so they never need a
    // real "added" timestamp for ordering — the curated order in `list` is what matters. `icon` is the
    // app's own logo URL, or null to fall back to the globe glyph until a favicon is captured on visit.
    private fun MutableList<FavoriteApp.WebApp>.webApp(
        label: String,
        url: String,
        icon: String? = null,
    ) {
        add(FavoriteApp.WebApp(url = url, label = label, addedAt = 0L, iconUrl = icon))
    }
}

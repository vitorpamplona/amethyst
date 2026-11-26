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
package com.vitorpamplona.amethyst.commons.napplet.session

import com.vitorpamplona.amethyst.commons.napplet.HostProfile
import com.vitorpamplona.amethyst.commons.napplet.NappletOrigins
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What a host does with one request its browser is about to make. */
enum class NappletRequestKind {
    /** A document the page made itself (`about:srcdoc`, `data:`, `blob:`): the browser's own, nothing leaves. */
    LOCAL,

    /** One of the internal hosts: answered by the launch's [com.vitorpamplona.amethyst.commons.napplet.NappletSiteServer]. */
    SERVE,

    /** A front page's picture: from the host's art cache, which keeps what an opaque-origin page can't. */
    PAPER_ART,

    /** A launch with a network of its own: the browser sends it, through the route the host claimed. */
    NETWORK,

    /** Anything else: cancelled before it leaves the device. A napplet or webxdc app has no network. */
    CANCEL,
}

/**
 * One rule of a host's request filter, in WebKit content-blocker terms: requests whose URL matches
 * [urlFilter] (a WebKit content-rule regex: no alternation, no counted repeats) are blocked, or,
 * for an allow rule, let through whatever an earlier rule said ("ignore-previous-rules").
 */
data class NappletContentRule(
    val urlFilter: String,
    val block: Boolean,
)

/**
 * Which requests a launch's browser may make, as data every host applies its own way: Desktop's
 * resource handler cancels, Android's interceptor answers 404, and iOS, whose scheme handler never
 * sees an `https` request, compiles [contentRules] into a `WKContentRuleList` as the second layer
 * behind the CSP.
 */
object NappletRequestPolicy {
    /**
     * Whether [url] is ours: [origins]' scheme on the shell host or one of its subdomains. Read from
     * the text, not a URI parser, which refuses URLs browsers send as they are (`{`, `|` in a query)
     * and would let such a request for the site's own origin out to the network.
     */
    fun isInternal(
        url: String,
        origins: NappletOrigins = NappletWebContract.HTTPS,
    ): Boolean {
        val prefix = origins.scheme + "://"
        if (!url.startsWith(prefix)) return false
        val authority =
            url
                .substring(prefix.length)
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('#')
        if ('@' in authority) return false
        return NappletWebContract.isInternalHost(authority.substringBefore(':').lowercase())
    }

    fun isWebLink(url: String): Boolean = url.startsWith("https://") || url.startsWith("http://")

    /** A napplet's srcdoc document and the data/blob URLs a page makes itself: none leave the device. */
    fun isLocal(url: String): Boolean = url == "about:srcdoc" || url == "about:blank" || url.startsWith("data:") || url.startsWith("blob:")

    fun classify(
        url: String,
        profile: HostProfile,
        isImage: Boolean,
        origins: NappletOrigins = NappletWebContract.HTTPS,
    ): NappletRequestKind =
        when {
            isLocal(url) -> NappletRequestKind.LOCAL
            isInternal(url, origins) -> NappletRequestKind.SERVE
            profile == HostProfile.PAPER && isImage && isWebLink(url) -> NappletRequestKind.PAPER_ART
            profile.exposesNetwork -> NappletRequestKind.NETWORK
            else -> NappletRequestKind.CANCEL
        }

    /**
     * [classify] as rules, for a browser that filters requests by URL before they leave (WebKit), in
     * order, the last match winning:
     * - A launch with no network blocks everything, then lets through the shell's host, its own
     *   origin and the documents a page makes itself.
     * - One with a network (a site, a front page) blocks only the internal names over http(s): they
     *   don't exist on the network, and looking them up would tell a resolver which app runs.
     */
    fun contentRules(
        profile: HostProfile,
        appOrigin: String,
        origins: NappletOrigins,
    ): List<NappletContentRule> {
        val internalWeb = NappletContentRule("^https?://([^/:]*\\.)?" + escape(NappletWebContract.HOST) + "[:/]", block = true)
        if (profile.exposesNetwork) return listOf(internalWeb)
        return buildList {
            add(NappletContentRule(".*", block = true))
            add(NappletContentRule("^" + escape(origins.origin) + "[:/]", block = false))
            if (profile.ownsOrigin) add(NappletContentRule("^" + escape(appOrigin) + "[:/]", block = false))
            add(NappletContentRule("^about:", block = false))
            add(NappletContentRule("^data:", block = false))
            add(NappletContentRule("^blob:", block = false))
        }
    }

    /** [rules] as a WebKit content rule list (`WKContentRuleListStore.compileContentRuleList`). */
    fun contentRuleListJson(rules: List<NappletContentRule>): String =
        JsonArray(
            rules.map { rule ->
                JsonObject(
                    mapOf(
                        "trigger" to JsonObject(mapOf("url-filter" to JsonPrimitive(rule.urlFilter))),
                        "action" to JsonObject(mapOf("type" to JsonPrimitive(if (rule.block) "block" else "ignore-previous-rules"))),
                    ),
                )
            },
        ).toString()

    // Content-rule regexes are a small dialect: only the characters an origin can hold need escaping.
    private fun escape(text: String): String =
        buildString {
            for (c in text) {
                if (c == '.' || c == '?' || c == '+' || c == '*' || c == '(' || c == ')' || c == '[' || c == ']' || c == '\\' || c == '^' || c == '$') append('\\')
                append(c)
            }
        }
}

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
package com.vitorpamplona.amethyst.commons.cartridges

import androidx.compose.runtime.Immutable
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip68Picture.fallback
import com.vitorpamplona.quartz.nip68Picture.hash
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Another cart of a game: the [names] the game's carts call it by, exactly as they pass them to `load`
 * or `reload`, where to get it, in order, and the [sha256] its bytes must have.
 */
@Immutable
class ExtraCart(
    val names: List<String>,
    val urls: List<String>,
    val sha256: String,
)

/**
 * What one cart of a note can reach through `load` and `reload`, read from the note's `imeta` tags:
 * [extras] by the names they list, and itself by [ownNames], so a game can come back to its first
 * cart. Only the carts its note lists: a name is looked up, and never becomes a URL.
 *
 * ```json
 * ["imeta", "url https://example.com/front.png", "x <sha256>", "m image/png", "cart #game"]
 * ["imeta", "url https://example.com/main.png", "x <sha256>", "m image/png", "cart #game_main-7"]
 * ```
 *
 * The entry cart is the one in the note's text. An extra cart is any other `imeta` with a `cart` field
 * and a valid `x`, and the field repeats for a game that spells a name two ways.
 */
@Immutable
class CartLinks(
    val ownNames: List<String>,
    val extras: List<ExtraCart>,
) {
    // Length-prefixed, because a name is a stranger's text: a line break in one must not make it read
    // as two pairs. Sorted, so listing the same carts in another order is the same game.
    private fun extraPairs(): String =
        extras
            .flatMap { cart -> cart.names.map { name -> "${lengthPrefixed(name)} ${cart.sha256}" } }
            .sorted()
            .joinToString("\n")

    /** Sets these links apart from another note's for the same entry cart, which may reach other carts. */
    fun fingerprint(): String = ownNames.map(::lengthPrefixed).sorted().joinToString("\n", postfix = "\n") + extraPairs()

    private fun lengthPrefixed(name: String) = "${name.length}:$name"

    /**
     * What the game's saves are kept under (see [CartSaveStore]), given the hash of the entry cart it
     * loaded. Reusing a game's front cart in another note, with a hostile cart under the name the
     * game loads, makes a different game with saves of its own. A game with no other carts keeps the
     * hash of its cart.
     *
     * It covers the carts the note declares, not the ones that downloaded, so a failed download does
     * not move a game's saves.
     */
    fun saveKey(entryHash: String): String = if (extras.isEmpty()) entryHash else sha256("$entryHash\n${extraPairs()}".encodeToByteArray()).toHexKey()

    /**
     * Every cart of the game by name: [ownNames] answering with [entry], and each extra cart that
     * could be fetched with a matching hash. One that could not is left out, so the game plays until
     * it asks for it. Only a cancellation throws.
     */
    suspend fun fetchCarts(
        entry: ByteArray,
        fetch: suspend (url: String) -> ByteArray?,
    ): Map<String, ByteArray> =
        coroutineScope {
            val fetched = extras.map { cart -> async { cart to cart.fetchFirstMatch(fetch) } }.awaitAll()
            buildMap {
                ownNames.forEach { put(it, entry) }
                for ((cart, bytes) in fetched) {
                    if (bytes != null) cart.names.forEach { put(it, bytes) }
                }
            }
        }

    private suspend fun ExtraCart.fetchFirstMatch(fetch: suspend (url: String) -> ByteArray?): ByteArray? {
        for (url in urls) {
            val bytes =
                runCatchingCancellable { fetch(url) }
                    .onFailure { Log.w(TAG, "Could not fetch the cart at $url", it) }
                    .getOrNull() ?: continue
            if (sha256(bytes).toHexKey() == sha256) {
                Log.i(TAG) { "Fetched ${names.first()} from $url, ${bytes.size} bytes" }
                return bytes
            }
            Log.w(TAG) { "The cart at $url does not match its hash, $sha256" }
        }
        Log.w(TAG) { "Could not get ${names.first()} from any of its ${urls.size} URLs" }
        return null
    }

    companion object {
        /** The `imeta` field with a name a cart goes by. Custom, like `webxdc`. */
        const val CART_FIELD = "cart"

        /** Our own limit: enough for any real game, and 16 MiB of them is what one note can add. */
        const val MAX_EXTRA_CARTS = 16

        /** The `url` and its `fallback`s, so one note's extra carts make at most 48 requests. */
        const val MAX_URLS_PER_CART = 3

        private const val TAG = "CartLinks"

        /**
         * [imetas] if any of them names a cart, else null. This is what a parsed note keeps for
         * [forEntry]: one reference shared by its images, because building the links of every image
         * of a stranger's note would make it cost its tags times its images, in time and in memory.
         */
        fun tagsToKeep(imetas: Map<String, IMetaTag>): Map<String, IMetaTag>? = imetas.takeIf { tags -> tags.values.any { CART_FIELD in it.properties } }

        /**
         * What the cart at [entryUrl] can reach, from the `imeta` tags of its note; null when they
         * name no cart. Past the limits, entries are ignored. On a name clash the first entry wins,
         * and the entry cart's own names beat the rest.
         */
        fun forEntry(
            entryUrl: String,
            imetas: Map<String, IMetaTag>,
        ): CartLinks? {
            val taken = HashSet<String>()
            val ownNames = imetas[entryUrl]?.names().orEmpty().filter(taken::add)
            val extras = ArrayList<ExtraCart>()
            for (imeta in imetas.values) {
                if (extras.size == MAX_EXTRA_CARTS) break
                if (imeta.url == entryUrl) continue
                val sha256 = imeta.sha256() ?: continue
                val names = imeta.names().filter(taken::add)
                if (names.isEmpty()) continue
                val urls = (listOf(imeta.url) + imeta.fallback().orEmpty()).take(MAX_URLS_PER_CART)
                extras.add(ExtraCart(names, urls, sha256))
            }
            return if (ownNames.isEmpty() && extras.isEmpty()) null else CartLinks(ownNames, extras)
        }

        private fun IMetaTag.names() = properties[CART_FIELD].orEmpty().filter { it.isNotEmpty() }

        private fun IMetaTag.sha256(): String? =
            hash()
                ?.firstOrNull()
                ?.takeIf(Hex::isHexKey)
                ?.lowercase()
    }
}

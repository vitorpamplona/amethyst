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
package com.vitorpamplona.amethyst.napplet.gateways

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Reads non-key identity data from an account as JSON value strings, for the napplet `identity.*`
 * domain. Everything here is **public** profile/list data — never key material. Returns the literal
 * `"null"` for an absent value, or `null` for a method this shell does not implement yet (the broker
 * then answers `Unsupported`). Shapes match `@napplet/nap` (e.g. `displayName`, not `display_name`).
 */
class AccountIdentityReader(
    private val account: Account,
) {
    fun read(
        method: String,
        argument: String?,
    ): String? =
        when (method) {
            "getProfile" -> profileJson()
            "getFollows" -> jsonStringArray(account.kind3FollowList.flow.value.authors)
            "getMutes" ->
                jsonStringArray(
                    account.muteList.flow.value
                        .filterIsInstance<UserTag>()
                        .map { it.pubKey },
                )
            "getBlocked" ->
                jsonStringArray(
                    account.blockPeopleList.flow.value
                        .filterIsInstance<UserTag>()
                        .map { it.pubKey },
                )
            "getRelays" -> relaysJson()
            // getList/getZaps/getBadges and any other read are not implemented yet → Unsupported.
            else -> null
        }

    private fun jsonStringArray(items: Iterable<String>): String = buildJsonArray { items.forEach { add(it) } }.toString()

    /** Builds a `@napplet/nap` `ProfileData` object (note `displayName`, not `display_name`) from kind-0. */
    private fun profileJson(): String {
        val md = account.userMetadata.getUserMetadataEvent()?.contactMetaData() ?: return "null"
        return buildJsonObject {
            md.name?.let { put("name", it) }
            md.displayName?.let { put("displayName", it) }
            md.about?.let { put("about", it) }
            md.picture?.let { put("picture", it) }
            md.banner?.let { put("banner", it) }
            md.nip05?.let { put("nip05", it) }
            md.lud16?.let { put("lud16", it) }
            md.website?.let { put("website", it) }
        }.toString()
    }

    /** Builds `{ "<relay url>": { "read": bool, "write": bool }, ... }` from the user's NIP-65 list. */
    private fun relaysJson(): String {
        val relays = account.nip65RelayList.getNIP65RelayList()?.relays() ?: return "null"
        return buildJsonObject {
            relays.forEach { info ->
                putJsonObject(info.relayUrl.url) {
                    put("read", info.type.isRead())
                    put("write", info.type.isWrite())
                }
            }
        }.toString()
    }
}

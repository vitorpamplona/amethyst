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
package com.vitorpamplona.quartz.openagents.nipExtExtensions.listing

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.openagents.core.EventRef
import com.vitorpamplona.quartz.openagents.core.OaJson
import com.vitorpamplona.quartz.openagents.core.PackageId
import com.vitorpamplona.quartz.openagents.nipExtExtensions.ExtRecordType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * The body of a kind-30184 package listing: the package, whether it is shown, the exact release it
 * currently points at, and the display text people pick it by.
 *
 * Every field is optional on the way in so a malformed listing still renders and indexes what it
 * says; [isWellFormed] is the shape check the protocol requires before trusting one.
 */
@Immutable
@Serializable
data class ExtensionListing(
    val v: Int? = OaJson.VERSION,
    val requires: List<String>? = emptyList(),
    val type: String? = ExtRecordType.LISTING,
    @SerialName("package")
    val packageId: String? = null,
    val state: String? = null,
    val release: EventRef? = null,
    val title: String? = null,
    val description: String? = null,
    val meta: JsonObject? = null,
) {
    fun parsedPackage() = PackageId.parse(packageId)

    fun isPublished() = state == STATE_PUBLISHED

    fun isHidden() = state == STATE_HIDDEN

    /**
     * The shape NIP-EXT gives a listing: v1, the listing type, a valid package id, and a release
     * that agrees with the state — an exact one when published, none when hidden.
     */
    fun isWellFormed(): Boolean {
        if (v != OaJson.VERSION || type != ExtRecordType.LISTING || parsedPackage() == null) return false
        return when (state) {
            STATE_PUBLISHED -> release?.isWellFormed() == true
            STATE_HIDDEN -> release == null
            else -> false
        }
    }

    /**
     * The canonical body. A hidden listing writes `"release": null` explicitly: the contracts
     * keep omission and `null` distinct, and that is the one place a `null` is specified.
     */
    fun toJson(): String {
        val written = OaJson.json.encodeToJsonElement(serializer(), this).jsonObject
        if (release != null || written.containsKey(RELEASE)) return written.toString()
        val ordered = LinkedHashMap<String, JsonElement>()
        written.forEach { (key, value) ->
            ordered[key] = value
            if (key == STATE) ordered[RELEASE] = JsonNull
        }
        if (!ordered.containsKey(RELEASE)) ordered[RELEASE] = JsonNull
        return JsonObject(ordered).toString()
    }

    companion object {
        const val STATE_PUBLISHED = "published"
        const val STATE_HIDDEN = "hidden"

        private const val STATE = "state"
        private const val RELEASE = "release"

        fun parse(content: String): ExtensionListing = OaJson.json.decodeFromString(serializer(), content)
    }
}

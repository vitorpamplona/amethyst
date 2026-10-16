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
package com.vitorpamplona.quartz.openagents.core

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.utils.ensure

/**
 * The `t` tags OpenAgents records carry to say what they are: `oa:ext:<type>:v1` on an EXT record,
 * `oa:cap:v1` plus `oa:profile:<profile>` and `oa:transport:<transport>` on a capability head.
 *
 * They ride the ordinary NIP-12 `t` tag, so a relay's `#t` index finds them — which is how every
 * OpenAgents reader queries for them — but they are protocol markers, not hashtags: they are
 * lower-case by construction and written once, never in the dual-case form a hashtag gets.
 */
object OaMarkerTag {
    const val TAG_NAME = "t"
    const val PREFIX = "oa:"

    fun assemble(value: String) = arrayOf(TAG_NAME, value)

    fun parse(tag: Array<String>): String? {
        ensure(tag.size >= 2) { return null }
        ensure(tag[0] == TAG_NAME) { return null }
        ensure(tag[1].startsWith(PREFIX)) { return null }
        return tag[1]
    }
}

/** Every OpenAgents marker on this event, in tag order. */
fun TagArray.oaMarkers(): List<String> = mapNotNull(OaMarkerTag::parse)

/** The markers that start with [prefix], e.g. every `oa:transport:` one. */
fun TagArray.oaMarkersWith(prefix: String): List<String> = oaMarkers().filter { it.startsWith(prefix) }

fun TagArray.hasOaMarker(value: String) = any { it.size >= 2 && it[0] == OaMarkerTag.TAG_NAME && it[1] == value }

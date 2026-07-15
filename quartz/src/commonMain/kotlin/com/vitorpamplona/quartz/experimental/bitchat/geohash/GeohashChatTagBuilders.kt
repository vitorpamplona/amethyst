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
package com.vitorpamplona.quartz.experimental.bitchat.geohash

import com.vitorpamplona.quartz.experimental.bitchat.geohash.tags.NicknameTag
import com.vitorpamplona.quartz.experimental.bitchat.geohash.tags.TeleportTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHashTag

/**
 * Adds a single, exact `["g", geohash]` tag.
 *
 * Note: this deliberately does NOT use the [GeoHashTag.assemble] mip-map helper
 * (which would emit every prefix `["g", u4pruy]`, `["g", u4pru]`, …). Bitchat
 * location channels tag only the full cell and subscribe with an exact `#g`
 * filter, so a single tag is required for wire-level interop.
 */
fun <T : Event> TagArrayBuilder<T>.geohashCell(geohash: String) = add(GeoHashTag.assembleSingle(geohash))

fun <T : Event> TagArrayBuilder<T>.nickname(nickname: String) = add(NicknameTag.assemble(nickname))

fun <T : Event> TagArrayBuilder<T>.teleport() = add(TeleportTag.assemble())

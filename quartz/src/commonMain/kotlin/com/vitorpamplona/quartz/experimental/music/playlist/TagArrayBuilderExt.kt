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
package com.vitorpamplona.quartz.experimental.music.playlist

import com.vitorpamplona.quartz.experimental.music.playlist.tags.CollaborativeTag
import com.vitorpamplona.quartz.experimental.music.playlist.tags.DescriptionTag
import com.vitorpamplona.quartz.experimental.music.playlist.tags.ImageTag
import com.vitorpamplona.quartz.experimental.music.playlist.tags.PrivateTag
import com.vitorpamplona.quartz.experimental.music.playlist.tags.PublicTag
import com.vitorpamplona.quartz.experimental.music.playlist.tags.TitleTag
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag

fun TagArrayBuilder<MusicPlaylistEvent>.title(title: String) = addUnique(TitleTag.assemble(title))

fun TagArrayBuilder<MusicPlaylistEvent>.image(url: String) = addUnique(ImageTag.assemble(url))

fun TagArrayBuilder<MusicPlaylistEvent>.description(description: String) = addUnique(DescriptionTag.assemble(description))

fun TagArrayBuilder<MusicPlaylistEvent>.public(public: Boolean = true) = addUnique(PublicTag.assemble(public))

fun TagArrayBuilder<MusicPlaylistEvent>.private(private: Boolean = true) = addUnique(PrivateTag.assemble(private))

fun TagArrayBuilder<MusicPlaylistEvent>.collaborative(collaborative: Boolean = true) = addUnique(CollaborativeTag.assemble(collaborative))

fun TagArrayBuilder<MusicPlaylistEvent>.trackAddress(address: Address) = add(ATag.assemble(address, null))

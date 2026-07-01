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
package com.vitorpamplona.quartz.nipXXPodcasting20.trailer

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.tags.EditTag
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.tags.PubDateTag
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.tags.TitleTag
import com.vitorpamplona.quartz.nipXXPodcasting20.trailer.tags.LengthTag
import com.vitorpamplona.quartz.nipXXPodcasting20.trailer.tags.SeasonTag
import com.vitorpamplona.quartz.nipXXPodcasting20.trailer.tags.TypeTag
import com.vitorpamplona.quartz.nipXXPodcasting20.trailer.tags.UrlTag

fun TagArrayBuilder<Podcasting20TrailerEvent>.title(title: String) = addUnique(TitleTag.assemble(title))

fun TagArrayBuilder<Podcasting20TrailerEvent>.url(url: String) = addUnique(UrlTag.assemble(url))

fun TagArrayBuilder<Podcasting20TrailerEvent>.pubdate(rfc2822Date: String) = addUnique(PubDateTag.assemble(rfc2822Date))

fun TagArrayBuilder<Podcasting20TrailerEvent>.length(lengthInBytes: Long) = addUnique(LengthTag.assemble(lengthInBytes))

fun TagArrayBuilder<Podcasting20TrailerEvent>.type(mimeType: String) = addUnique(TypeTag.assemble(mimeType))

fun TagArrayBuilder<Podcasting20TrailerEvent>.season(season: Int) = addUnique(SeasonTag.assemble(season))

fun TagArrayBuilder<Podcasting20TrailerEvent>.edit(originalEventId: HexKey) = addUnique(EditTag.assemble(originalEventId))

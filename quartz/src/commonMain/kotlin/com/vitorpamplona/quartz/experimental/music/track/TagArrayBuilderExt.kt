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
package com.vitorpamplona.quartz.experimental.music.track

import com.vitorpamplona.quartz.experimental.music.track.tags.AlbumTag
import com.vitorpamplona.quartz.experimental.music.track.tags.ArtistTag
import com.vitorpamplona.quartz.experimental.music.track.tags.BitrateTag
import com.vitorpamplona.quartz.experimental.music.track.tags.DurationTag
import com.vitorpamplona.quartz.experimental.music.track.tags.ExplicitTag
import com.vitorpamplona.quartz.experimental.music.track.tags.FormatTag
import com.vitorpamplona.quartz.experimental.music.track.tags.ImageTag
import com.vitorpamplona.quartz.experimental.music.track.tags.LanguageTag
import com.vitorpamplona.quartz.experimental.music.track.tags.ReleasedTag
import com.vitorpamplona.quartz.experimental.music.track.tags.SampleRateTag
import com.vitorpamplona.quartz.experimental.music.track.tags.TitleTag
import com.vitorpamplona.quartz.experimental.music.track.tags.TrackNumberTag
import com.vitorpamplona.quartz.experimental.music.track.tags.UrlTag
import com.vitorpamplona.quartz.experimental.music.track.tags.VideoUrlTag
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder

fun TagArrayBuilder<MusicTrackEvent>.title(title: String) = addUnique(TitleTag.assemble(title))

fun TagArrayBuilder<MusicTrackEvent>.artist(artist: String) = addUnique(ArtistTag.assemble(artist))

fun TagArrayBuilder<MusicTrackEvent>.url(url: String) = addUnique(UrlTag.assemble(url))

fun TagArrayBuilder<MusicTrackEvent>.videoUrl(url: String) = addUnique(VideoUrlTag.assemble(url))

fun TagArrayBuilder<MusicTrackEvent>.image(url: String) = addUnique(ImageTag.assemble(url))

fun TagArrayBuilder<MusicTrackEvent>.album(album: String) = addUnique(AlbumTag.assemble(album))

fun TagArrayBuilder<MusicTrackEvent>.trackNumber(number: Int) = addUnique(TrackNumberTag.assemble(number))

fun TagArrayBuilder<MusicTrackEvent>.released(isoDate: String) = addUnique(ReleasedTag.assemble(isoDate))

fun TagArrayBuilder<MusicTrackEvent>.duration(seconds: Int) = addUnique(DurationTag.assemble(seconds))

fun TagArrayBuilder<MusicTrackEvent>.format(format: String) = addUnique(FormatTag.assemble(format))

fun TagArrayBuilder<MusicTrackEvent>.bitrate(bitrate: String) = addUnique(BitrateTag.assemble(bitrate))

fun TagArrayBuilder<MusicTrackEvent>.sampleRate(hz: Int) = addUnique(SampleRateTag.assemble(hz))

fun TagArrayBuilder<MusicTrackEvent>.language(iso639: String) = addUnique(LanguageTag.assemble(iso639))

fun TagArrayBuilder<MusicTrackEvent>.explicit(explicit: Boolean = true) = addUnique(ExplicitTag.assemble(explicit))

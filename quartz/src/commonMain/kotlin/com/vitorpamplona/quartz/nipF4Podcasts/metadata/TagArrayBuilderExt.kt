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
package com.vitorpamplona.quartz.nipF4Podcasts.metadata

import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.tags.AuthorTag
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.tags.DescriptionTag
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.tags.ImageTag
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.tags.TitleTag
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.tags.WebsiteTag

fun TagArrayBuilder<PodcastMetadataEvent>.title(title: String) = addUnique(TitleTag.assemble(title))

fun TagArrayBuilder<PodcastMetadataEvent>.image(url: String) = addUnique(ImageTag.assemble(url))

fun TagArrayBuilder<PodcastMetadataEvent>.description(description: String) = addUnique(DescriptionTag.assemble(description))

fun TagArrayBuilder<PodcastMetadataEvent>.website(url: String) = add(WebsiteTag.assemble(url))

fun TagArrayBuilder<PodcastMetadataEvent>.author(author: AuthorTag) = add(AuthorTag.assemble(author))

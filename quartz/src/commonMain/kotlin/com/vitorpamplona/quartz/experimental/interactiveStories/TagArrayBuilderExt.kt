/**
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
package com.vitorpamplona.quartz.experimental.interactiveStories

import com.vitorpamplona.quartz.experimental.interactiveStories.tags.ReadStatusTag
import com.vitorpamplona.quartz.experimental.interactiveStories.tags.StoryOptionTag
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip23LongContent.tags.ImageTag
import com.vitorpamplona.quartz.nip23LongContent.tags.PublishedAtTag
import com.vitorpamplona.quartz.nip23LongContent.tags.SummaryTag
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag

fun <T : InteractiveStoryBaseEvent> TagArrayBuilder<T>.title(title: String) = addUnique(TitleTag.assemble(title))

fun <T : InteractiveStoryBaseEvent> TagArrayBuilder<T>.option(option: StoryOptionTag) = add(option.toTagArray())

fun <T : InteractiveStoryBaseEvent> TagArrayBuilder<T>.options(options: List<StoryOptionTag>) = addAll(options.map { it.toTagArray() })

fun <T : InteractiveStoryBaseEvent> TagArrayBuilder<T>.publishedAt(publishedAt: Long) = addUnique(PublishedAtTag.assemble(publishedAt))

fun TagArrayBuilder<InteractiveStoryPrologueEvent>.summary(summary: String) = addUnique(SummaryTag.assemble(summary))

fun TagArrayBuilder<InteractiveStoryPrologueEvent>.image(imageUrl: String) = add(ImageTag.assemble(imageUrl))

fun TagArrayBuilder<InteractiveStoryPrologueEvent>.images(imageUrls: List<String>) = addAll(imageUrls.map { ImageTag.assemble(it) })

fun TagArrayBuilder<InteractiveStoryReadingStateEvent>.storyTitle(title: String) = addUnique(TitleTag.assemble(title))

fun TagArrayBuilder<InteractiveStoryReadingStateEvent>.storySummary(summary: String) = addUnique(SummaryTag.assemble(summary))

fun TagArrayBuilder<InteractiveStoryReadingStateEvent>.storyImage(imageUrl: String) = add(ImageTag.assemble(imageUrl))

fun TagArrayBuilder<InteractiveStoryReadingStateEvent>.storyImages(imageUrls: List<String>) = addAll(imageUrls.map { ImageTag.assemble(it) })

fun TagArrayBuilder<InteractiveStoryReadingStateEvent>.rootScene(scene: ATag) = addUnique(scene.toATagArray())

fun TagArrayBuilder<InteractiveStoryReadingStateEvent>.currentScene(scene: ATag) = addUnique(scene.toATagArray())

fun TagArrayBuilder<InteractiveStoryReadingStateEvent>.status(status: ReadStatusTag.STATUS) = addUnique(status.toTagArray())

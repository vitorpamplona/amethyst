package com.vitorpamplona.quartz.nip54Wiki

import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip23LongContent.tags.PublishedAtTag

fun TagArrayBuilder<LongTextNoteEvent>.title(title: String) = addUnique(TitleTag.assemble(title))

fun TagArrayBuilder<LongTextNoteEvent>.summary(summary: String) = addUnique(SummaryTag.assemble(summary))

fun TagArrayBuilder<LongTextNoteEvent>.image(imageUrl: String) = addUnique(ImageTag.assemble(imageUrl))

fun TagArrayBuilder<LongTextNoteEvent>.publishedAt(publishedAt: Long) = addUnique(PublishedAtTag.assemble(publishedAt))
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
package com.vitorpamplona.quartz.experimental.fitness.workout

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.DifficultyTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.EquipmentTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.FormatTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.FormatUnitsTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.TitleTag
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip50Search.SearchableEvent

/**
 * NIP-101e (draft) exercise template, kind 33401, as published by POWR. An
 * addressable, reusable definition referenced from the per-set `exercise` tags
 * of a [WorkoutRecordEvent] via its `33401:pubkey:d-tag` coordinate.
 *
 * Amethyst fetches these to show a real exercise name (and equipment) in a
 * workout card instead of the d-tag slug. Parsing is lax: every tag optional.
 */
@Immutable
class ExerciseTemplateEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    SearchableEvent {
    override fun indexableContent() = listOfNotNull(title(), content).joinToString("\n")

    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    fun format() = tags.firstNotNullOfOrNull(FormatTag::parse)

    fun formatUnits() = tags.firstNotNullOfOrNull(FormatUnitsTag::parse)

    fun equipment() = tags.firstNotNullOfOrNull(EquipmentTag::parse)

    fun difficulty() = tags.firstNotNullOfOrNull(DifficultyTag::parse)

    companion object {
        const val KIND = 33401
        const val ALT_DESCRIPTION = "Exercise template"
    }
}

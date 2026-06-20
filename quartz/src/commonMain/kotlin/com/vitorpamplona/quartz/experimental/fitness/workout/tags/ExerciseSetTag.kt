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
package com.vitorpamplona.quartz.experimental.fitness.workout.tags

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.ensure

/**
 * A single logged set in the POWR / NIP-101e strength dialect.
 *
 * Tag layout: `["exercise", "<kind>:<pubkey>:<d-tag>", "<relay>", weight, reps,
 * rpe, set_type, set_number]`, where the second element is an addressable
 * coordinate to a kind-33401 exercise template. The set fields, in order:
 *
 * - **weight** — kilograms; empty string means bodyweight, negative means assisted.
 * - **reps** — repetition count.
 * - **rpe** — Rate of Perceived Exertion, 0..10.
 * - **set_type** — `warmup` | `normal` | `drop` | `failure`.
 * - **set_number** — 1-based index (POWR extension, not in the base spec).
 *
 * This is structurally distinct from RUNSTR's `exercise` tag (a plain activity
 * verb such as `running`), so [parse] only matches the coordinate form.
 */
@Stable
class ExerciseSetTag(
    val reference: String,
    val relayHint: String?,
    val weightKg: Double?,
    val reps: Int?,
    val rpe: Double?,
    val setType: String?,
    val setNumber: Int?,
) {
    /** The d-tag slug of the referenced exercise template, e.g. `back-squat-bb`. */
    fun dTag(): String? = reference.split(":", limit = 3).getOrNull(2)?.ifBlank { null }

    /** Volume of this set in kilograms (weight × reps), when both are known and positive. */
    fun volumeKg(): Double? {
        val w = weightKg ?: return null
        val r = reps ?: return null
        return if (w > 0.0 && r > 0) w * r else null
    }

    companion object {
        const val TAG_NAME = "exercise"

        const val SET_TYPE_WARMUP = "warmup"
        const val SET_TYPE_NORMAL = "normal"
        const val SET_TYPE_DROP = "drop"
        const val SET_TYPE_FAILURE = "failure"

        /**
         * True when [value] is an addressable coordinate (`kind:pubkey:d-tag`) rather
         * than a plain verb. Used to tell the POWR `exercise` form from RUNSTR's.
         */
        fun isCoordinate(value: String): Boolean {
            val parts = value.split(":", limit = 3)
            return parts.size == 3 && parts[0].toIntOrNull() != null && parts[1].length == 64
        }

        /** The referenced exercise-template coordinate, when the tag is the POWR set form. */
        fun parseAddressId(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(isCoordinate(tag[1])) { return null }
            return tag[1]
        }

        /** The relay hint for the referenced exercise template, so it can be fetched. */
        fun parseAsHint(tag: Array<String>): AddressHint? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(isCoordinate(tag[1])) { return null }
            ensure(tag[2].isNotEmpty()) { return null }
            val relayHint = RelayUrlNormalizer.normalizeOrNull(tag[2]) ?: return null
            return AddressHint(tag[1], relayHint)
        }

        fun parse(tag: Array<String>): ExerciseSetTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(isCoordinate(tag[1])) { return null }
            return ExerciseSetTag(
                reference = tag[1],
                relayHint = tag.getOrNull(2)?.ifBlank { null },
                weightKg = tag.getOrNull(3)?.toDoubleOrNull(),
                reps = tag.getOrNull(4)?.toIntOrNull(),
                rpe = tag.getOrNull(5)?.toDoubleOrNull(),
                setType = tag.getOrNull(6)?.ifBlank { null },
                setNumber = tag.getOrNull(7)?.toIntOrNull(),
            )
        }
    }
}

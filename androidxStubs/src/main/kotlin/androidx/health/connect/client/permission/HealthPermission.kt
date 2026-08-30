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
package androidx.health.connect.client.permission

import androidx.health.connect.client.records.Record
import kotlin.reflect.KClass

/**
 * JVM stand-in for androidx.health.connect.client.permission.HealthPermission.
 *
 * Builds the same permission strings the platform does, so the app's set is
 * identical on both. They are never granted here — there is nothing to grant
 * them — but the set is compared against what the provider reports, and that
 * comparison has to be over the same names to mean anything.
 */
object HealthPermission {
    private const val READ_PREFIX = "android.permission.health.READ_"
    private const val WRITE_PREFIX = "android.permission.health.WRITE_"

    fun getReadPermission(record: KClass<out Record>): String = READ_PREFIX + screamingSnake(record)

    fun getWritePermission(record: KClass<out Record>): String = WRITE_PREFIX + screamingSnake(record)

    /** ExerciseSessionRecord -> EXERCISE_SESSION, matching the platform's names. */
    private fun screamingSnake(record: KClass<out Record>): String {
        val name = record.simpleName.orEmpty().removeSuffix("Record")
        return buildString {
            name.forEachIndexed { index, char ->
                if (char.isUpperCase() && index > 0) append('_')
                append(char.uppercaseChar())
            }
        }
    }
}

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
package com.vitorpamplona.quartz.nip01Core.store.sqlite.sql

class SqlSelectionBuilder(
    private val condition: Condition,
) {
    private val selectionArgs = mutableListOf<String>()

    fun build(): WhereClause {
        selectionArgs.clear() // Clear previous args for a fresh build
        val conditions = buildCondition(condition)
        return WhereClause(conditions, selectionArgs)
    }

    /**
     * Recursively builds the SQL string for a given condition.
     * @param cond The [Condition] to build the SQL string for.
     * @return The SQL string representation of the condition.
     */
    private fun buildCondition(cond: Condition): String =
        when (cond) {
            is Condition.Equals -> {
                if (cond.value == null) {
                    "${cond.column} IS NULL"
                } else {
                    selectionArgs.add(cond.value.toString())
                    "${cond.column} = ?"
                }
            }
            is Condition.NotEquals -> {
                if (cond.value == null) {
                    "${cond.column} IS NULL"
                } else {
                    selectionArgs.add(cond.value.toString())
                    "${cond.column} != ?"
                }
            }
            is Condition.GreaterThan -> {
                selectionArgs.add(cond.value.toString())
                "${cond.column} > ?"
            }
            is Condition.GreaterThanOrEquals -> {
                selectionArgs.add(cond.value.toString())
                "${cond.column} >= ?"
            }
            is Condition.LessThan -> {
                selectionArgs.add(cond.value.toString())
                "${cond.column} < ?"
            }
            is Condition.LessThanOrEquals -> {
                selectionArgs.add(cond.value.toString())
                "${cond.column} <= ?"
            }
            is Condition.Like -> {
                selectionArgs.add(cond.value)
                "${cond.column} LIKE ?"
            }
            is Condition.Match -> {
                selectionArgs.add(cond.value)
                "${cond.table} MATCH ?"
            }
            is Condition.IsNull -> {
                "${cond.column} IS NULL"
            }
            is Condition.IsNotNull -> {
                "${cond.column} IS NOT NULL"
            }
            is Condition.In -> {
                if (cond.values.isEmpty()) {
                    // Handle empty IN clause gracefully, perhaps by making it always false
                    // or throwing an error, depending on desired behavior.
                    // For now, let's make it an always false condition to avoid SQL errors.
                    "1 = 0" // Always false
                } else {
                    val placeholders = cond.values.joinToString(", ") { "?" }
                    cond.values.forEach { selectionArgs.add(it.toString()) }
                    "${cond.column} IN ($placeholders)"
                }
            }
            is Condition.And -> {
                if (cond.conditions.isEmpty()) {
                    "1 = 1" // Always true for an empty AND
                } else {
                    cond.conditions.joinToString(" AND ") { "(${buildCondition(it)})" }
                }
            }
            is Condition.Or -> {
                if (cond.conditions.isEmpty()) {
                    "1 = 0" // Always false for an empty OR
                } else {
                    cond.conditions.joinToString(" OR ") { "(${buildCondition(it)})" }
                }
            }
        }
}

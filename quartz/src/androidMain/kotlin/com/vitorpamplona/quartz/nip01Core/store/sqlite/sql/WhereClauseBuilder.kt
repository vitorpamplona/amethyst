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
package com.vitorpamplona.quartz.nip01Core.store.sqlite.sql

class WhereClauseBuilder {
    private val conditions = mutableListOf<Condition>()

    fun raw(condition: String) = apply { conditions.add(Condition.Raw(condition)) }

    fun equals(
        column: String,
        value: Any?,
    ) = apply { conditions.add(Condition.Equals(column, value)) }

    fun notEquals(
        column: String,
        value: Any?,
    ) = apply { conditions.add(Condition.NotEquals(column, value)) }

    fun greaterThan(
        column: String,
        value: Any,
    ) = apply { conditions.add(Condition.GreaterThan(column, value)) }

    fun greaterThanOrEquals(
        column: String,
        value: Any,
    ) = apply { conditions.add(Condition.GreaterThanOrEquals(column, value)) }

    fun lessThan(
        column: String,
        value: Any,
    ) = apply { conditions.add(Condition.LessThan(column, value)) }

    fun lessThanOrEquals(
        column: String,
        value: Any,
    ) = apply { conditions.add(Condition.LessThanOrEquals(column, value)) }

    fun like(
        column: String,
        pattern: String,
    ) = apply { conditions.add(Condition.Like(column, pattern)) }

    fun match(
        table: String,
        pattern: String,
    ) = apply { conditions.add(Condition.Match(table, pattern)) }

    fun isNull(column: String) = apply { conditions.add(Condition.IsNull(column)) }

    fun isNotNull(column: String) = apply { conditions.add(Condition.IsNotNull(column)) }

    fun isIn(
        column: String,
        values: List<Any>,
    ) = apply { conditions.add(Condition.In(column, values)) }

    fun equalsOrIn(
        column: String,
        values: List<Any>,
    ) = apply {
        if (values.size == 1) {
            equals(column, values.first())
        } else {
            isIn(column, values)
        }
    }

    fun and(block: WhereClauseBuilder.() -> Unit) =
        apply {
            val builder = WhereClauseBuilder().apply(block)
            val builtCondition = builder.buildAnd()
            if (builtCondition != null) {
                conditions.add(builtCondition)
            }
        }

    fun or(block: WhereClauseBuilder.() -> Unit) =
        apply {
            val builder = WhereClauseBuilder().apply(block)
            val builtCondition = builder.buildOr()
            if (builtCondition != null) {
                conditions.add(builtCondition)
            }
        }

    fun buildAnd(): Condition? =
        when (conditions.size) {
            0 -> null
            1 -> conditions.first()
            else -> Condition.And(conditions.toList())
        }

    fun buildOr(): Condition? =
        when (conditions.size) {
            0 -> null
            1 -> conditions.first()
            else -> Condition.Or(conditions.toList())
        }
}

fun where(block: WhereClauseBuilder.() -> Unit): WhereClause {
    val condition = WhereClauseBuilder().apply(block).buildAnd() ?: Condition.Empty()
    return SqlSelectionBuilder(condition).build()
}

class WhereClause(
    val conditions: String,
    val args: List<String>,
)

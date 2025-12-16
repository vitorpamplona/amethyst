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
package com.vitorpamplona.quartz.nip01Core.store.sqlite

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import junit.framework.TestCase

fun <T : Event> EventStore.assertQuery(
    expected: T?,
    filter: Filter,
) {
    val queryResult = query<T>(filter)
    val countResult = count(filter)
    if (expected == null) {
        TestCase.assertEquals(0, queryResult.size)
        TestCase.assertEquals(0, countResult)
    } else {
        TestCase.assertEquals(1, queryResult.size)
        TestCase.assertEquals(1, countResult)
        TestCase.assertEquals(expected.toJson(), queryResult.first().toJson())
    }
}

fun <T : Event> EventStore.assertQuery(
    expected: List<T>,
    filter: Filter,
) {
    val queryResult = query<T>(filter)
    val countResult = count(filter)
    TestCase.assertEquals(expected.size, queryResult.size)
    TestCase.assertEquals(expected.size, countResult)
    expected.forEachIndexed { index, event ->
        TestCase.assertEquals(event.toJson(), queryResult[index].toJson())
    }
}

fun <T : Event> SQLiteEventStore.assertQuery(
    expected: T?,
    filter: Filter,
) {
    val queryResult = query<T>(filter)
    val countResult = count(filter)
    if (expected == null) {
        TestCase.assertEquals(0, queryResult.size)
        TestCase.assertEquals(0, countResult)
    } else {
        TestCase.assertEquals(1, queryResult.size)
        TestCase.assertEquals(1, countResult)
        TestCase.assertEquals(expected.toJson(), queryResult.first().toJson())
    }
}

fun <T : Event> SQLiteEventStore.assertQuery(
    expected: List<T>,
    filter: Filter,
) {
    val queryResult = query<T>(filter)
    val countResult = count(filter)
    TestCase.assertEquals(expected.size, queryResult.size)
    TestCase.assertEquals(expected.size, countResult)
    expected.forEachIndexed { index, event ->
        TestCase.assertEquals(event.toJson(), queryResult[index].toJson())
    }
}

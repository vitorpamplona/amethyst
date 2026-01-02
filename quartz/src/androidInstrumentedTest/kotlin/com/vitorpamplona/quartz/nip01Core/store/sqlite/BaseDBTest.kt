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

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before

open class BaseDBTest {
    private lateinit var dbs: Map<String, EventStore>

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        dbs =
            mapOf(
                "Default" to EventStore(context, null, indexStrategy = DefaultIndexingStrategy(false, false)),
                "IndexAll" to EventStore(context, null, indexStrategy = DefaultIndexingStrategy(true, false)),
                "Order by ID" to EventStore(context, null, indexStrategy = DefaultIndexingStrategy(false, true)),
                "IndexAll, Order by ID" to EventStore(context, null, indexStrategy = DefaultIndexingStrategy(true, true)),
            )
    }

    @After
    fun tearDown() {
        dbs.forEach { it.value.close() }
    }

    fun forEachDB(action: (EventStore) -> Unit) {
        dbs.forEach {
            println("--------------------")
            println(it.key)
            println("--------------------")
            action(it.value)
        }
    }
}

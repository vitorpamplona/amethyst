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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.quartz.nip01Core.core.JsonEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonEngineSettingTest {
    @After
    fun reset() {
        JsonEngine.current = JsonEngine.DEFAULT
    }

    @Test
    fun theStoredChoiceIsAppliedBeforeAnythingIsCollected() {
        // A dispatcher that is never advanced: the collector never runs, so only the
        // synchronous assignment in followJsonEngine can have set the engine.
        val scope = CoroutineScope(StandardTestDispatcher())
        try {
            UiSettingsFlow.build(UiSettings(kiteJson = true)).followJsonEngine(scope)
            assertEquals(JsonEngine.KITE, JsonEngine.current)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun theEngineFollowsTheSwitch() =
        runBlocking {
            val scope = CoroutineScope(Dispatchers.Default)
            try {
                val settings = UiSettingsFlow()
                settings.followJsonEngine(scope)
                assertEquals(JsonEngine.DEFAULT, JsonEngine.current)

                settings.kiteJson.tryEmit(true)
                waitFor(JsonEngine.KITE)

                settings.kiteJson.tryEmit(false)
                waitFor(JsonEngine.DEFAULT)
            } finally {
                scope.cancel()
            }
        }

    private suspend fun waitFor(engine: JsonEngine) {
        withTimeout(5_000) {
            while (JsonEngine.current != engine) delay(10)
        }
    }
}

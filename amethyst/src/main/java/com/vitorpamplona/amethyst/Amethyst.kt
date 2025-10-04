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
package com.vitorpamplona.amethyst

import android.app.Application
import com.vitorpamplona.amethyst.service.logging.Logging
import com.vitorpamplona.quartz.utils.Log

class Amethyst : Application() {
    init {
        Log.d("AmethystApp", "Creating App $this")
    }

    companion object {
        lateinit var instance: AppModules
            private set
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("AmethystApp", "onCreate $this")
        instance = AppModules(this)

        if (isDebug) {
            Logging.setup()
        }

        instance.initiate(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d("AmethystApp", "onTerminate $this")
        instance.terminate(this)
    }

    /**
     * Release memory when the UI becomes hidden or when system resources become low.
     *
     * @param level the memory-related event that was raised.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.d("AmethystApp", "onTrimMemory $level")
        instance.trim()
    }
}

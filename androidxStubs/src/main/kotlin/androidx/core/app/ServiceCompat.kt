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
package androidx.core.app

import android.app.Notification
import android.app.Service

/**
 * JVM stand-in for androidx.core.app.ServiceCompat.
 *
 * Delegates to [Service], where the foreground-service gap is already declared:
 * desktop processes are not killed for being backgrounded, so there is nothing
 * to keep alive and no notification to justify it.
 */
object ServiceCompat {
    const val STOP_FOREGROUND_REMOVE = Service.STOP_FOREGROUND_REMOVE
    const val STOP_FOREGROUND_DETACH = Service.STOP_FOREGROUND_DETACH

    @JvmStatic
    fun startForeground(
        service: Service,
        id: Int,
        notification: Notification,
        foregroundServiceType: Int,
    ) = service.startForeground(id, notification, foregroundServiceType)

    @JvmStatic
    fun stopForeground(
        service: Service,
        flags: Int,
    ) = service.stopForeground(flags)
}

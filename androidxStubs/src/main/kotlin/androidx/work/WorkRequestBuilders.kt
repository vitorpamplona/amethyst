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
@file:Suppress("ktlint:standard:function-naming")

package androidx.work

import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * JVM stand-ins for the reified request builders that work-runtime-ktx adds.
 * They exist only so `OneTimeWorkRequestBuilder<MyWorker>()` reads the same on
 * both platforms; the scheduling itself is [WorkManager]'s job.
 *
 * Capitalised because these are the names work-runtime-ktx exports; renaming
 * them would defeat the point of the shim.
 */
inline fun <reified W : ListenableWorker> OneTimeWorkRequestBuilder(): OneTimeWorkRequest.Builder = OneTimeWorkRequest.Builder(W::class.java)

inline fun <reified W : ListenableWorker> PeriodicWorkRequestBuilder(
    repeatInterval: Long,
    repeatIntervalTimeUnit: TimeUnit,
): PeriodicWorkRequest.Builder = PeriodicWorkRequest.Builder(W::class.java, repeatInterval, repeatIntervalTimeUnit)

inline fun <reified W : ListenableWorker> PeriodicWorkRequestBuilder(repeatInterval: Duration): PeriodicWorkRequest.Builder = PeriodicWorkRequest.Builder(W::class.java, repeatInterval)

inline fun <reified W : ListenableWorker> PeriodicWorkRequestBuilder(
    repeatInterval: Long,
    repeatIntervalTimeUnit: TimeUnit,
    flexTimeInterval: Long,
    flexTimeIntervalUnit: TimeUnit,
): PeriodicWorkRequest.Builder =
    PeriodicWorkRequest.Builder(
        W::class.java,
        repeatInterval,
        repeatIntervalTimeUnit,
        flexTimeInterval,
        flexTimeIntervalUnit,
    )

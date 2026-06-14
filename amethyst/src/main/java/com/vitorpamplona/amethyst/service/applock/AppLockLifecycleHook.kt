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
package com.vitorpamplona.amethyst.service.applock

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Application-wide observer that feeds foreground/background transitions to
 * [AppLockState]'s controller so the app re-locks after enough time in the
 * background. Registered once from
 * [com.vitorpamplona.amethyst.Amethyst.onCreate].
 *
 * Kept separate from
 * [com.vitorpamplona.amethyst.service.nests.AppForegroundRecycleHook] even though
 * both track the same start/stop counter: the thresholds and side effects differ
 * (5 s socket-recycle vs 5 min security re-lock), and keeping them independent
 * leaves each one a small, single-purpose, testable unit.
 */
class AppLockLifecycleHook : Application.ActivityLifecycleCallbacks {
    override fun onActivityStarted(activity: Activity) {
        AppLockState.controller.onActivityStarted()
    }

    override fun onActivityStopped(activity: Activity) {
        AppLockState.controller.onActivityStopped()
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}

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
package androidx.activity

import android.app.Activity
import android.content.Intent
import androidx.core.util.Consumer
import java.util.concurrent.CopyOnWriteArrayList

/**
 * JVM stand-in for androidx.activity.ComponentActivity.
 *
 * The new-intent listeners are real, because the thing they deliver is real on
 * a desktop too. On Android a second intent arrives when the user shares into
 * an already-running app or opens a `nostr:` link; the desktop equivalents are
 * a URL handed to the running instance by the OS scheme handler, a file opened
 * onto the app, or a second launch folded into the first window. All of those
 * have to reach the same listeners — the composer that appends a shared image,
 * the navigation that routes a nostr URI — so the registry keeps them and
 * [dispatchNewIntent] is what the desktop shell calls to deliver one.
 *
 * Until the shell wires that up nothing arrives, which is reported once rather
 * than left as a silently dead deep-link path.
 */
open class ComponentActivity : Activity() {
    private val newIntentListeners = CopyOnWriteArrayList<Consumer<Intent>>()

    fun addOnNewIntentListener(listener: Consumer<Intent>) {
        newIntentListeners.add(listener)
        com.vitorpamplona.amethyst.stubs.PlatformGaps.report(
            "ComponentActivity.addOnNewIntentListener",
            "the desktop shell must call dispatchNewIntent() when the OS hands the running app a " +
                "nostr: URL, an opened file or a second launch; until it does, shares and deep links " +
                "into a running window go nowhere",
        )
    }

    fun removeOnNewIntentListener(listener: Consumer<Intent>) {
        newIntentListeners.remove(listener)
    }

    /** Delivers an intent to every registered listener; see the class doc. */
    fun dispatchNewIntent(intent: Intent) {
        intent.let { newIntentListeners.forEach { listener -> listener.accept(it) } }
    }
}

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
package androidx.activity.compose

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import com.vitorpamplona.amethyst.stubs.PlatformGaps

/**
 * JVM stand-in for `ComponentActivity.setContent`.
 *
 * An Activity's content is its window's content, and desktop has neither — a
 * Compose Desktop window is opened by `application { Window { … } }` from the
 * app's own entry point, not by an object handing a composable to the
 * framework. The screens that call this are each really "open a window showing
 * X", and the desktop app opens them directly.
 *
 * So the composable is kept rather than dropped: [pendingContent] holds what
 * the last `setContent` was given, which is what a desktop shell would need to
 * put in a window if these activity-shaped screens are ever hosted rather than
 * rewritten. The gap is reported either way, because until something reads it
 * nothing appears.
 */
fun ComponentActivity.setContent(content: @Composable () -> Unit) {
    ActivityContent.pendingContent = content
    PlatformGaps.report(
        "Activity.setContent",
        "${this::class.java.name} wants to show Compose content in its own window; " +
            "desktop opens windows from the application entry point, so this screen needs a window there",
    )
}

/** Where [setContent] parks its composable. */
object ActivityContent {
    @Volatile
    var pendingContent: (@Composable () -> Unit)? = null
}

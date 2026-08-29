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
package androidx.compose.ui.platform

import android.content.res.Configuration
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.vitorpamplona.amethyst.shared.platform.JvmConfiguration

/**
 * JVM stand-ins for the Compose Android composition locals that expose the
 * Android window and view.
 *
 * `LocalConfiguration` resolves to the process-wide configuration, which on
 * desktop only really carries the locale. `LocalView` has no analogue at all —
 * there is no Android View behind a desktop composition — so it is null, and
 * call sites must already tolerate that rather than being handed a fake.
 */
val LocalConfiguration: ProvidableCompositionLocal<Configuration> = staticCompositionLocalOf { JvmConfiguration }

val LocalView: ProvidableCompositionLocal<Any?> = staticCompositionLocalOf { null }

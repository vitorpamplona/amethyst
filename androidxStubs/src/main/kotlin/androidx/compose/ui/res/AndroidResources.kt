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
package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.vitorpamplona.amethyst.shared.resources.AndroidResourceTable
import com.vitorpamplona.amethyst.shared.resources.JvmDrawables

/**
 * JVM stand-ins for the id-taking resource accessors that only Compose's
 * Android artifact declares.
 *
 * Declared in Compose's own package so existing
 * `import androidx.compose.ui.res.stringResource` lines resolve unchanged.
 * Compose Multiplatform's desktop artifact already declares a
 * `painterResource(String)` taking a classpath path; the `Int` overload added
 * here sits beside it rather than replacing it.
 */
@Composable
fun stringResource(id: Int): String = AndroidResourceTable.getString(id)

@Composable
fun stringResource(
    id: Int,
    vararg formatArgs: Any,
): String = AndroidResourceTable.getString(id, *formatArgs)

@Composable
fun pluralStringResource(
    id: Int,
    count: Int,
): String = AndroidResourceTable.getQuantityString(id, count)

@Composable
fun pluralStringResource(
    id: Int,
    count: Int,
    vararg formatArgs: Any,
): String = AndroidResourceTable.getQuantityString(id, count, *formatArgs)

@Composable
fun painterResource(id: Int): Painter = JvmDrawables.painterFor(id)

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
package com.vitorpamplona.amethyst.shared.platform

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import com.vitorpamplona.amethyst.shared.resources.AndroidResourceTable

/**
 * The JVM implementation behind the `android.content.Context` stub.
 *
 * Shared code passes a `Context` around for resource lookup, package identity
 * and platform services. On the JVM those needs are met by process-wide
 * singletons, so a single instance suffices; it exists at all because ~235
 * call sites already take a `Context` parameter and rewriting them would be
 * churn without benefit.
 */
object JvmContext : Context() {
    private const val PACKAGE_NAME = "com.vitorpamplona.amethyst"

    override fun getPackageName(): String = PACKAGE_NAME

    override fun getResources(): Resources = JvmResources

    override fun getString(resId: Int): String = AndroidResourceTable.getString(resId)

    override fun getString(
        resId: Int,
        vararg formatArgs: Any?,
    ): String = AndroidResourceTable.getString(resId, *formatArgs)
}

object JvmResources : Resources() {
    override fun getString(resId: Int): String = AndroidResourceTable.getString(resId)

    override fun getString(
        resId: Int,
        vararg formatArgs: Any?,
    ): String = AndroidResourceTable.getString(resId, *formatArgs)

    override fun getQuantityString(
        resId: Int,
        quantity: Int,
    ): String = AndroidResourceTable.getQuantityString(resId, quantity)

    override fun getQuantityString(
        resId: Int,
        quantity: Int,
        vararg formatArgs: Any?,
    ): String = AndroidResourceTable.getQuantityString(resId, quantity, *formatArgs)

    override fun getConfiguration(): Configuration = JvmConfiguration
}

object JvmConfiguration : Configuration() {
    // Rebuilt on read rather than cached: the locale can change at runtime and
    // callers reach for it precisely when they are about to format something.
    override fun getLocales(): LocaleList = LocaleList(AndroidResourceTable.locale)
}

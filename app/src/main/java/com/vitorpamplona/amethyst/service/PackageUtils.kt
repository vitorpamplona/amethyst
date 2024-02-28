/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.service

import android.content.Context
import android.content.Intent
import android.net.Uri

object PackageUtils {
    private fun isPackageInstalled(
        context: Context,
        target: String,
    ): Boolean {
        return context.packageManager.getInstalledApplications(0).find { info ->
            info.packageName == target
        } != null
    }

    fun isOrbotInstalled(context: Context): Boolean {
        return isPackageInstalled(context, "org.torproject.android")
    }

    fun isExternalSignerInstalled(context: Context): Boolean {
        val intent =
            Intent().apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("nostrsigner:")
            }
        val infos = context.packageManager.queryIntentActivities(intent, 0)
        return infos.size > 0
    }
}

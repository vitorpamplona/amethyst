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
package androidx.core.content

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager

/**
 * JVM stand-in for androidx.core.content.ContextCompat.
 *
 * ContextCompat exists to paper over API-level differences; on the JVM there
 * are none, so each method is the direct call it would have compiled down to
 * on a current Android release.
 */
object ContextCompat {
    fun startActivity(
        context: Context,
        intent: Intent,
        options: android.os.Bundle?,
    ) = context.startActivity(intent, options)

    fun <T> getSystemService(
        context: Context,
        serviceClass: Class<T>,
    ): T? = context.getSystemService(serviceClass)

    /** Same target as [Context.startForegroundService]; see the note there. */
    fun startForegroundService(
        context: Context,
        intent: Intent,
    ) = context.startForegroundService(intent)

    fun checkSelfPermission(
        context: Context,
        permission: String,
    ): Int = context.checkSelfPermission(permission)

    fun getColor(
        context: Context,
        id: Int,
    ): Int = 0

    /** Same in-process bus as [Context.registerReceiver]; see the note there. */
    fun registerReceiver(
        context: Context,
        receiver: BroadcastReceiver,
        filter: IntentFilter,
        flags: Int,
    ): Intent? {
        context.registerReceiver(receiver, filter, flags)
        return null
    }

    const val RECEIVER_NOT_EXPORTED = Context.RECEIVER_NOT_EXPORTED
    const val RECEIVER_EXPORTED = Context.RECEIVER_EXPORTED
}

/** JVM stand-in for the `SharedPreferences.edit { }` KTX extension. */
inline fun SharedPreferences.edit(
    commit: Boolean = false,
    action: SharedPreferences.Editor.() -> Unit,
) {
    val editor = edit()
    editor.action()
    if (commit) editor.commit() else editor.apply()
}

/** JVM stand-in for androidx.core.content.PermissionChecker constants. */
object PermissionChecker {
    const val PERMISSION_GRANTED = PackageManager.PERMISSION_GRANTED
    const val PERMISSION_DENIED = PackageManager.PERMISSION_DENIED
}

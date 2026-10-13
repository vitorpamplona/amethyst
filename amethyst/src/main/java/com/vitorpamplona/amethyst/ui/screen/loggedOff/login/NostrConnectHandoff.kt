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
package com.vitorpamplona.amethyst.ui.screen.loggedOff.login

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Parcelable
import androidx.core.net.toUri
import com.vitorpamplona.quartz.utils.Log

/**
 * Hands a `nostrconnect://` address to a NIP-46 signer app (Amber and the like) so the user
 * approves there instead of scanning. Never to Amethyst itself: it handles the scheme too, as a
 * bunker pairing an app, which is the opposite of what the login screen asks. One other app gets
 * the address directly; several get a chooser without Amethyst. False when no other app takes it.
 */
@SuppressLint("QueryPermissionsNeeded") // The manifest's <queries> declares the nostrconnect scheme.
fun openNostrConnectInSigner(
    context: Context,
    uri: String,
): Boolean {
    if (!uri.startsWith("nostrconnect://")) return false
    val view = Intent(Intent.ACTION_VIEW, uri.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
    // Only what an implicit startActivity can launch: a filter without CATEGORY_DEFAULT would
    // count as a signer here and leave the chooser empty.
    val handlers = context.packageManager.queryIntentActivities(view, PackageManager.MATCH_DEFAULT_ONLY)
    val (own, others) = handlers.partition { it.activityInfo.packageName == context.packageName }
    if (others.isEmpty()) return false

    val target =
        if (others.size == 1) {
            view.setPackage(others.single().activityInfo.packageName)
        } else {
            val excluded: Array<Parcelable> = own.map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }.toTypedArray()
            Intent.createChooser(view, null).putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, excluded)
        }

    return try {
        context.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        // Not the exception: its message prints the intent, nostrconnect:// address and secret included.
        Log.w("NostrConnectHandoff") { "No app took the nostrconnect address: ${e::class.simpleName}" }
        false
    }
}

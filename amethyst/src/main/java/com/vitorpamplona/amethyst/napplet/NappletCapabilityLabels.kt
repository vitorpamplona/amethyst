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
package com.vitorpamplona.amethyst.napplet

import androidx.annotation.StringRes
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability

/** Localized display name for a capability, shared by the consent dialog and the permissions screen. */
@StringRes
fun NappletCapability.labelRes(): Int =
    when (this) {
        NappletCapability.SHELL -> R.string.napplet_cap_shell
        NappletCapability.IDENTITY -> R.string.napplet_cap_identity
        NappletCapability.KEYS -> R.string.napplet_cap_keys
        NappletCapability.RELAY -> R.string.napplet_cap_relay
        NappletCapability.STORAGE -> R.string.napplet_cap_storage
        NappletCapability.VALUE -> R.string.napplet_cap_value
        NappletCapability.RESOURCE -> R.string.napplet_cap_resource
        NappletCapability.UPLOAD -> R.string.napplet_cap_upload
        NappletCapability.THEME -> R.string.napplet_cap_theme
        NappletCapability.NOTIFY -> R.string.napplet_cap_notify
        NappletCapability.INC -> R.string.napplet_cap_inc
    }

/** Localized one-line description of what a capability lets a napplet do. */
@StringRes
fun NappletCapability.descriptionRes(): Int =
    when (this) {
        NappletCapability.SHELL -> R.string.napplet_cap_shell_desc
        NappletCapability.IDENTITY -> R.string.napplet_cap_identity_desc
        NappletCapability.KEYS -> R.string.napplet_cap_keys_desc
        NappletCapability.RELAY -> R.string.napplet_cap_relay_desc
        NappletCapability.STORAGE -> R.string.napplet_cap_storage_desc
        NappletCapability.VALUE -> R.string.napplet_cap_value_desc
        NappletCapability.RESOURCE -> R.string.napplet_cap_resource_desc
        NappletCapability.UPLOAD -> R.string.napplet_cap_upload_desc
        NappletCapability.THEME -> R.string.napplet_cap_theme_desc
        NappletCapability.NOTIFY -> R.string.napplet_cap_notify_desc
        NappletCapability.INC -> R.string.napplet_cap_inc_desc
    }

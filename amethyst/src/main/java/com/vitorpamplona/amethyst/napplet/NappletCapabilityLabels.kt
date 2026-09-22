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
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.napplet_cap_identity
import com.vitorpamplona.amethyst.commons.resources.napplet_cap_identity_desc
import com.vitorpamplona.amethyst.commons.resources.napplet_cap_inc
import com.vitorpamplona.amethyst.commons.resources.napplet_cap_inc_desc
import com.vitorpamplona.amethyst.commons.resources.napplet_cap_keys
import com.vitorpamplona.amethyst.commons.resources.napplet_cap_keys_desc
import com.vitorpamplona.amethyst.commons.resources.napplet_cap_notify
import com.vitorpamplona.amethyst.commons.resources.napplet_cap_notify_desc
import com.vitorpamplona.amethyst.commons.resources.napplet_cap_relay
import com.vitorpamplona.amethyst.commons.resources.napplet_cap_relay_desc
import com.vitorpamplona.amethyst.commons.resources.napplet_cap_resource
import com.vitorpamplona.amethyst.commons.resources.napplet_cap_resource_desc
import com.vitorpamplona.amethyst.commons.resources.napplet_cap_signer
import com.vitorpamplona.amethyst.commons.resources.napplet_cap_signer_desc
import com.vitorpamplona.amethyst.commons.resources.napplet_cap_storage
import com.vitorpamplona.amethyst.commons.resources.napplet_cap_storage_desc
import com.vitorpamplona.amethyst.commons.resources.napplet_cap_theme
import com.vitorpamplona.amethyst.commons.resources.napplet_cap_theme_desc
import com.vitorpamplona.amethyst.commons.resources.napplet_cap_upload
import com.vitorpamplona.amethyst.commons.resources.napplet_cap_upload_desc
import com.vitorpamplona.amethyst.commons.resources.napplet_cap_value
import com.vitorpamplona.amethyst.commons.resources.napplet_cap_value_desc

/** Localized display name for a capability, shared by the consent dialog and the permissions screen. */
@StringRes
fun NappletCapability.labelRes(): Int =
    when (this) {
        NappletCapability.IDENTITY -> Res.string.napplet_cap_identity
        NappletCapability.KEYS -> Res.string.napplet_cap_keys
        NappletCapability.RELAY -> Res.string.napplet_cap_relay
        NappletCapability.SIGNER -> Res.string.napplet_cap_signer
        NappletCapability.STORAGE -> Res.string.napplet_cap_storage
        NappletCapability.VALUE -> Res.string.napplet_cap_value
        NappletCapability.RESOURCE -> Res.string.napplet_cap_resource
        NappletCapability.UPLOAD -> Res.string.napplet_cap_upload
        NappletCapability.THEME -> Res.string.napplet_cap_theme
        NappletCapability.NOTIFY -> Res.string.napplet_cap_notify
        NappletCapability.INC -> Res.string.napplet_cap_inc
    }

/** Localized one-line description of what a capability lets a napplet do. */
@StringRes
fun NappletCapability.descriptionRes(): Int =
    when (this) {
        NappletCapability.IDENTITY -> Res.string.napplet_cap_identity_desc
        NappletCapability.KEYS -> Res.string.napplet_cap_keys_desc
        NappletCapability.RELAY -> Res.string.napplet_cap_relay_desc
        NappletCapability.SIGNER -> Res.string.napplet_cap_signer_desc
        NappletCapability.STORAGE -> Res.string.napplet_cap_storage_desc
        NappletCapability.VALUE -> Res.string.napplet_cap_value_desc
        NappletCapability.RESOURCE -> Res.string.napplet_cap_resource_desc
        NappletCapability.UPLOAD -> Res.string.napplet_cap_upload_desc
        NappletCapability.THEME -> Res.string.napplet_cap_theme_desc
        NappletCapability.NOTIFY -> Res.string.napplet_cap_notify_desc
        NappletCapability.INC -> Res.string.napplet_cap_inc_desc
    }

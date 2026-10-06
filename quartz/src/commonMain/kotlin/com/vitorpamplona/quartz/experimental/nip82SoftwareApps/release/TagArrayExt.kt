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
package com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release

import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.tags.CommitTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.tags.AppIdTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.tags.AssetTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.tags.ChannelTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.tags.VersionTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.shared.PlatformTag
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag

fun TagArray.appId() = firstNotNullOfOrNull(AppIdTag::parse)

fun TagArray.version() = firstNotNullOfOrNull(VersionTag::parse)

fun TagArray.channel() = firstNotNullOfOrNull(ChannelTag::parse)

fun TagArray.assets() = mapNotNull(AssetTag::parse)

private val SOFTWARE_APPLICATION_KIND = SoftwareApplicationEvent.KIND.toString()

/** The `a` tag pointing to the kind 32267 application, with its relay hint. */
fun TagArray.app() = firstNotNullOfOrNull { ATag.parseIfOfKind(it, SOFTWARE_APPLICATION_KIND) }

fun TagArray.platforms() = mapNotNull(PlatformTag::parse)

fun TagArray.commit() = firstNotNullOfOrNull(CommitTag::parse)

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
package com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application

import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.tags.IconTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.tags.ImageTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.tags.LicenseTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.tags.NameTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.tags.RepositoryTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.tags.SummaryTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.tags.UrlTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.shared.PlatformTag
import com.vitorpamplona.quartz.nip01Core.core.TagArray

fun TagArray.name() = firstNotNullOfOrNull(NameTag::parse)

fun TagArray.summary() = firstNotNullOfOrNull(SummaryTag::parse)

fun TagArray.icon() = firstNotNullOfOrNull(IconTag::parse)

fun TagArray.images() = mapNotNull(ImageTag::parse)

fun TagArray.url() = firstNotNullOfOrNull(UrlTag::parse)

fun TagArray.repository() = firstNotNullOfOrNull(RepositoryTag::parse)

fun TagArray.platforms() = mapNotNull(PlatformTag::parse)

fun TagArray.license() = firstNotNullOfOrNull(LicenseTag::parse)

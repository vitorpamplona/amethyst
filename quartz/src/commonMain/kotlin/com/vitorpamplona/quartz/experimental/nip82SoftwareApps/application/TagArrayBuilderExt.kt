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
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder

fun TagArrayBuilder<SoftwareApplicationEvent>.name(name: String) = addUnique(NameTag.assemble(name))

fun TagArrayBuilder<SoftwareApplicationEvent>.summary(summary: String) = addUnique(SummaryTag.assemble(summary))

fun TagArrayBuilder<SoftwareApplicationEvent>.icon(iconUrl: String) = addUnique(IconTag.assemble(iconUrl))

fun TagArrayBuilder<SoftwareApplicationEvent>.image(imageUrl: String) = add(ImageTag.assemble(imageUrl))

fun TagArrayBuilder<SoftwareApplicationEvent>.images(imageUrls: List<String>) = addAll(ImageTag.assemble(imageUrls))

fun TagArrayBuilder<SoftwareApplicationEvent>.url(websiteUrl: String) = addUnique(UrlTag.assemble(websiteUrl))

fun TagArrayBuilder<SoftwareApplicationEvent>.repository(repositoryUrl: String) = addUnique(RepositoryTag.assemble(repositoryUrl))

fun TagArrayBuilder<SoftwareApplicationEvent>.platform(platform: String) = add(PlatformTag.assemble(platform))

fun TagArrayBuilder<SoftwareApplicationEvent>.platforms(platforms: List<String>) = addAll(PlatformTag.assemble(platforms))

fun TagArrayBuilder<SoftwareApplicationEvent>.license(spdxId: String) = addUnique(LicenseTag.assemble(spdxId))

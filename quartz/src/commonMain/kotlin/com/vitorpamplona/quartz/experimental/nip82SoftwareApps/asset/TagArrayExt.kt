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
package com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset

import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.tags.ApkCertificateHashTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.tags.CommitTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.tags.MinAllowedVersionCodeTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.tags.MinAllowedVersionTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.tags.MinPlatformVersionTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.tags.SupportedNipTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.tags.TargetPlatformVersionTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.tags.VariantTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.tags.VersionCodeTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.tags.WebContentTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.tags.AppIdTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.tags.VersionTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.shared.PlatformTag
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip94FileMetadata.tags.HashSha256Tag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.MimeTypeTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.SizeTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.UrlTag

fun TagArray.appId() = firstNotNullOfOrNull(AppIdTag::parse)

fun TagArray.url() = firstNotNullOfOrNull(UrlTag::parse)

fun TagArray.mimeType() = firstNotNullOfOrNull(MimeTypeTag::parse)

fun TagArray.hash() = firstNotNullOfOrNull(HashSha256Tag::parse)

fun TagArray.sizeInBytes() = firstNotNullOfOrNull(SizeTag::parse)

fun TagArray.version() = firstNotNullOfOrNull(VersionTag::parse)

fun TagArray.platforms() = mapNotNull(PlatformTag::parse)

fun TagArray.minPlatformVersion() = firstNotNullOfOrNull(MinPlatformVersionTag::parse)

fun TagArray.targetPlatformVersion() = firstNotNullOfOrNull(TargetPlatformVersionTag::parse)

fun TagArray.supportedNips() = mapNotNull(SupportedNipTag::parse)

fun TagArray.variant() = firstNotNullOfOrNull(VariantTag::parse)

fun TagArray.commit() = firstNotNullOfOrNull(CommitTag::parse)

fun TagArray.minAllowedVersion() = firstNotNullOfOrNull(MinAllowedVersionTag::parse)

fun TagArray.versionCode() = firstNotNullOfOrNull(VersionCodeTag::parse)

fun TagArray.minAllowedVersionCode() = firstNotNullOfOrNull(MinAllowedVersionCodeTag::parse)

fun TagArray.apkCertificateHashes() = mapNotNull(ApkCertificateHashTag::parse)

fun TagArray.webContent() = firstNotNullOfOrNull(WebContentTag::parse)

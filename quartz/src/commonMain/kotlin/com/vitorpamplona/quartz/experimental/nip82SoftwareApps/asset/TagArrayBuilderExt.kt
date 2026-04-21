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
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip94FileMetadata.tags.HashSha256Tag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.MimeTypeTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.SizeTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.UrlTag

fun TagArrayBuilder<SoftwareAssetEvent>.appId(appId: String) = addUnique(AppIdTag.assemble(appId))

fun TagArrayBuilder<SoftwareAssetEvent>.url(url: String) = addUnique(UrlTag.assemble(url))

fun TagArrayBuilder<SoftwareAssetEvent>.mimeType(mimeType: String) = addUnique(MimeTypeTag.assemble(mimeType))

fun TagArrayBuilder<SoftwareAssetEvent>.hash(hash: String) = addUnique(HashSha256Tag.assemble(hash))

fun TagArrayBuilder<SoftwareAssetEvent>.sizeInBytes(sizeInBytes: Int) = addUnique(SizeTag.assemble(sizeInBytes))

fun TagArrayBuilder<SoftwareAssetEvent>.version(version: String) = addUnique(VersionTag.assemble(version))

fun TagArrayBuilder<SoftwareAssetEvent>.platform(platform: String) = add(PlatformTag.assemble(platform))

fun TagArrayBuilder<SoftwareAssetEvent>.platforms(platforms: List<String>) = addAll(PlatformTag.assemble(platforms))

fun TagArrayBuilder<SoftwareAssetEvent>.minPlatformVersion(version: String) = addUnique(MinPlatformVersionTag.assemble(version))

fun TagArrayBuilder<SoftwareAssetEvent>.targetPlatformVersion(version: String) = addUnique(TargetPlatformVersionTag.assemble(version))

fun TagArrayBuilder<SoftwareAssetEvent>.supportedNip(nipId: String) = add(SupportedNipTag.assemble(nipId))

fun TagArrayBuilder<SoftwareAssetEvent>.supportedNips(nipIds: List<String>) = addAll(SupportedNipTag.assemble(nipIds))

fun TagArrayBuilder<SoftwareAssetEvent>.variant(variant: String) = addUnique(VariantTag.assemble(variant))

fun TagArrayBuilder<SoftwareAssetEvent>.commit(commit: String) = addUnique(CommitTag.assemble(commit))

fun TagArrayBuilder<SoftwareAssetEvent>.minAllowedVersion(version: String) = addUnique(MinAllowedVersionTag.assemble(version))

fun TagArrayBuilder<SoftwareAssetEvent>.versionCode(versionCode: Long) = addUnique(VersionCodeTag.assemble(versionCode))

fun TagArrayBuilder<SoftwareAssetEvent>.minAllowedVersionCode(versionCode: Long) = addUnique(MinAllowedVersionCodeTag.assemble(versionCode))

fun TagArrayBuilder<SoftwareAssetEvent>.apkCertificateHash(hash: String) = add(ApkCertificateHashTag.assemble(hash))

fun TagArrayBuilder<SoftwareAssetEvent>.apkCertificateHashes(hashes: List<String>) = addAll(ApkCertificateHashTag.assemble(hashes))

fun TagArrayBuilder<SoftwareAssetEvent>.webContent(url: String) = addUnique(WebContentTag.assemble(url))

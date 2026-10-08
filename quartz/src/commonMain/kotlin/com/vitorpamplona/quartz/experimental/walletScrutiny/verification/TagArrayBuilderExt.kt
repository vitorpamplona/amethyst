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
package com.vitorpamplona.quartz.experimental.walletScrutiny.verification

import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.tags.AppIdTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.tags.VersionTag
import com.vitorpamplona.quartz.experimental.walletScrutiny.tags.AssetFileTag
import com.vitorpamplona.quartz.experimental.walletScrutiny.tags.AssetPlatformTag
import com.vitorpamplona.quartz.experimental.walletScrutiny.verification.tags.BasedOnTag
import com.vitorpamplona.quartz.experimental.walletScrutiny.verification.tags.BuildStatus
import com.vitorpamplona.quartz.experimental.walletScrutiny.verification.tags.BuildStatusTag
import com.vitorpamplona.quartz.experimental.walletScrutiny.verification.tags.FileAttachmentTag
import com.vitorpamplona.quartz.experimental.walletScrutiny.verification.tags.IssueTrackerUrlTag
import com.vitorpamplona.quartz.experimental.walletScrutiny.verification.tags.OutputFileTag
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder

fun TagArrayBuilder<BuildVerificationEvent>.productId(productId: String) = addUnique(AppIdTag.assemble(productId))

fun TagArrayBuilder<BuildVerificationEvent>.version(version: String) = addUnique(VersionTag.assemble(version))

fun TagArrayBuilder<BuildVerificationEvent>.platform(platform: String) = addUnique(AssetPlatformTag.assemble(platform))

fun TagArrayBuilder<BuildVerificationEvent>.buildStatus(status: BuildStatus) = addUnique(BuildStatusTag.assemble(status))

/** The registered download hashes the verdict covers (`x`), one tag each. */
fun TagArrayBuilder<BuildVerificationEvent>.verifiedHashes(hashes: List<HexKey>) = addAll(hashes.map { AssetFileTag.assemble(it) })

fun TagArrayBuilder<BuildVerificationEvent>.fileAttachments(eventIds: List<HexKey>) = addAll(eventIds.map { FileAttachmentTag.assemble(it) })

fun TagArrayBuilder<BuildVerificationEvent>.outputFiles(files: List<OutputFileTag>) = addAll(files.map { it.toTagArray() })

fun TagArrayBuilder<BuildVerificationEvent>.issueTrackerUrl(url: String) = addUnique(IssueTrackerUrlTag.assemble(url))

fun TagArrayBuilder<BuildVerificationEvent>.basedOn(basedOn: BasedOnTag) = addUnique(basedOn.toTagArray())

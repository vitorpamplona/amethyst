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

import com.vitorpamplona.quartz.experimental.walletScrutiny.verification.tags.BasedOnTag
import com.vitorpamplona.quartz.experimental.walletScrutiny.verification.tags.BuildStatus
import com.vitorpamplona.quartz.experimental.walletScrutiny.verification.tags.BuildStatusTag
import com.vitorpamplona.quartz.experimental.walletScrutiny.verification.tags.FileAttachmentTag
import com.vitorpamplona.quartz.experimental.walletScrutiny.verification.tags.IssueTrackerUrlTag
import com.vitorpamplona.quartz.experimental.walletScrutiny.verification.tags.OutputFileTag
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastFirstNotNullOfOrNull

fun TagArray.buildStatusCode() = fastFirstNotNullOfOrNull(BuildStatusTag::parse)

fun TagArray.buildStatus() = buildStatusCode()?.let { BuildStatus.fromCode(it) }

fun TagArray.fileAttachments() = mapNotNull(FileAttachmentTag::parse)

fun TagArray.outputFiles() = mapNotNull(OutputFileTag::parse)

fun TagArray.issueTrackerUrl() = fastFirstNotNullOfOrNull(IssueTrackerUrlTag::parse)

fun TagArray.basedOn() = fastFirstNotNullOfOrNull(BasedOnTag::parse)

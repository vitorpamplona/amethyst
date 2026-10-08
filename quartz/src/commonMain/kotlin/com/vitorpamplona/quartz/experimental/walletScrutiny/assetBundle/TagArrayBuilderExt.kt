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
package com.vitorpamplona.quartz.experimental.walletScrutiny.assetBundle

import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.tags.AppIdTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.tags.VersionTag
import com.vitorpamplona.quartz.experimental.walletScrutiny.tags.AssetFileTag
import com.vitorpamplona.quartz.experimental.walletScrutiny.tags.AssetPlatformTag
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder

fun TagArrayBuilder<AssetBundleEvent>.productId(productId: String) = addUnique(AppIdTag.assemble(productId))

fun TagArrayBuilder<AssetBundleEvent>.version(version: String) = addUnique(VersionTag.assemble(version))

fun TagArrayBuilder<AssetBundleEvent>.platform(platform: String) = addUnique(AssetPlatformTag.assemble(platform))

fun TagArrayBuilder<AssetBundleEvent>.files(files: List<AssetFileTag>) = addAll(files.map { it.toTagArray() })

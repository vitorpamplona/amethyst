/**
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
package com.vitorpamplona.quartz.nip01Core.metadata

import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.metadata.tags.AboutTag
import com.vitorpamplona.quartz.nip01Core.metadata.tags.BannerTag
import com.vitorpamplona.quartz.nip01Core.metadata.tags.DisplayNameTag
import com.vitorpamplona.quartz.nip01Core.metadata.tags.Lud06Tag
import com.vitorpamplona.quartz.nip01Core.metadata.tags.Lud16Tag
import com.vitorpamplona.quartz.nip01Core.metadata.tags.MoneroAddressesTag
import com.vitorpamplona.quartz.nip01Core.metadata.tags.NameTag
import com.vitorpamplona.quartz.nip01Core.metadata.tags.Nip05Tag
import com.vitorpamplona.quartz.nip01Core.metadata.tags.PictureTag
import com.vitorpamplona.quartz.nip01Core.metadata.tags.PronounsTag
import com.vitorpamplona.quartz.nip01Core.metadata.tags.WebsiteTag

fun TagArrayBuilder<MetadataEvent>.name(name: String) = addUnique(NameTag.assemble(name))

fun TagArrayBuilder<MetadataEvent>.displayName(name: String) = addUnique(DisplayNameTag.assemble(name))

fun TagArrayBuilder<MetadataEvent>.picture(picture: String) = addUnique(PictureTag.assemble(picture))

fun TagArrayBuilder<MetadataEvent>.about(about: String) = addUnique(AboutTag.assemble(about))

fun TagArrayBuilder<MetadataEvent>.website(url: String) = addUnique(WebsiteTag.assemble(url))

fun TagArrayBuilder<MetadataEvent>.nip05(nip05: String) = addUnique(Nip05Tag.assemble(nip05))

fun TagArrayBuilder<MetadataEvent>.lud16(lud16: String) = addUnique(Lud16Tag.assemble(lud16))

fun TagArrayBuilder<MetadataEvent>.lud06(lud06: String) = addUnique(Lud06Tag.assemble(lud06))

fun TagArrayBuilder<MetadataEvent>.banner(banner: String) = addUnique(BannerTag.assemble(banner))

fun TagArrayBuilder<MetadataEvent>.pronouns(pronouns: String) = addUnique(PronounsTag.assemble(pronouns))

fun TagArrayBuilder<MetadataEvent>.monero(monero: String) = addUnique(MoneroAddressesTag.assemble(monero))

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
package com.vitorpamplona.quartz.marmot.mip00KeyPackages

import com.vitorpamplona.quartz.marmot.mip00KeyPackages.tags.ClientTag
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.tags.EncodingTag
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.tags.KeyPackageRefTag
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.tags.MlsCiphersuiteTag
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.tags.MlsExtensionsTag
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.tags.MlsProposalsTag
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.tags.MlsProtocolVersionTag
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.tags.RelaysTag
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

fun TagArrayBuilder<KeyPackageEvent>.mlsProtocolVersion(version: String = MlsProtocolVersionTag.CURRENT_VERSION) = addUnique(MlsProtocolVersionTag.assemble(version))

fun TagArrayBuilder<KeyPackageEvent>.mlsCiphersuite(ciphersuite: String = MlsCiphersuiteTag.DEFAULT_CIPHERSUITE) = addUnique(MlsCiphersuiteTag.assemble(ciphersuite))

fun TagArrayBuilder<KeyPackageEvent>.mlsExtensions(extensionIds: List<String>) = addUnique(MlsExtensionsTag.assemble(extensionIds))

fun TagArrayBuilder<KeyPackageEvent>.mlsProposals(proposalIds: List<String>) = addUnique(MlsProposalsTag.assemble(proposalIds))

fun TagArrayBuilder<KeyPackageEvent>.encoding() = addUnique(EncodingTag.assemble())

fun TagArrayBuilder<KeyPackageEvent>.keyPackageRef(ref: HexKey) = addUnique(KeyPackageRefTag.assemble(ref))

fun TagArrayBuilder<KeyPackageEvent>.keyPackageRelays(relays: List<NormalizedRelayUrl>) = addUnique(RelaysTag.assemble(relays))

fun TagArrayBuilder<KeyPackageEvent>.client(name: String) = addUnique(ClientTag.assemble(name))

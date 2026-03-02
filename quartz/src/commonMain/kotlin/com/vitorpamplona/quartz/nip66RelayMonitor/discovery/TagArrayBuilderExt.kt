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
package com.vitorpamplona.quartz.nip66RelayMonitor.discovery

import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.tags.AcceptedKindTag
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.tags.NetworkType
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.tags.NetworkTypeTag
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.tags.RelayTypeTag
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.tags.RequirementTag
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.tags.RttTag
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.tags.RttType
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.tags.SupportedNipTag

fun TagArrayBuilder<RelayDiscoveryEvent>.rtt(
    type: RttType,
    milliseconds: Long,
) = addUnique(RttTag.assemble(type, milliseconds))

fun TagArrayBuilder<RelayDiscoveryEvent>.networkType(network: NetworkType) = addUnique(NetworkTypeTag.assemble(network))

fun TagArrayBuilder<RelayDiscoveryEvent>.networkTypes(networks: List<NetworkType>) = addAll(NetworkTypeTag.assemble(networks))

fun TagArrayBuilder<RelayDiscoveryEvent>.relayType(type: String) = addUnique(RelayTypeTag.assemble(type))

fun TagArrayBuilder<RelayDiscoveryEvent>.relayTypes(types: List<String>) = addAll(RelayTypeTag.assemble(types))

fun TagArrayBuilder<RelayDiscoveryEvent>.supportedNip(nip: Int) = add(SupportedNipTag.assemble(nip))

fun TagArrayBuilder<RelayDiscoveryEvent>.supportedNips(nips: List<Int>) = addAll(SupportedNipTag.assemble(nips))

fun TagArrayBuilder<RelayDiscoveryEvent>.requirement(
    value: String,
    negated: Boolean = false,
) = add(RequirementTag.assemble(value, negated))

fun TagArrayBuilder<RelayDiscoveryEvent>.requirements(reqs: List<RequirementTag>) = addAll(RequirementTag.assemble(reqs))

fun TagArrayBuilder<RelayDiscoveryEvent>.acceptedKind(
    kind: Int,
    negated: Boolean = false,
) = add(AcceptedKindTag.assemble(kind, negated))

fun TagArrayBuilder<RelayDiscoveryEvent>.acceptedKinds(kinds: List<AcceptedKindTag>) = addAll(AcceptedKindTag.assemble(kinds))

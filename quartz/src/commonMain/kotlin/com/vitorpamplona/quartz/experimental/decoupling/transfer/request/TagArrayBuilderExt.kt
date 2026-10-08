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
package com.vitorpamplona.quartz.experimental.decoupling.transfer.request

import com.vitorpamplona.quartz.experimental.decoupling.transfer.tags.ClientKeyAliasTag
import com.vitorpamplona.quartz.experimental.decoupling.transfer.tags.ClientKeyTag
import com.vitorpamplona.quartz.experimental.decoupling.transfer.tags.RequestedKeyTag
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip51Lists.relayLists.tags.RelayTag

fun TagArrayBuilder<EncryptionKeyRequestEvent>.clientKey(clientPubKey: HexKey) = addUnique(ClientKeyTag.assemble(clientPubKey))

fun TagArrayBuilder<EncryptionKeyRequestEvent>.clientKeyAlias(clientPubKey: HexKey) = addUnique(ClientKeyAliasTag.assemble(clientPubKey))

fun TagArrayBuilder<EncryptionKeyRequestEvent>.responseRelays(relays: List<NormalizedRelayUrl>) = addAll(relays.map { RelayTag.assemble(it) })

fun TagArrayBuilder<EncryptionKeyRequestEvent>.requestedEncryptionKey(encryptionPubKey: HexKey) = addUnique(RequestedKeyTag.assemble(encryptionPubKey))

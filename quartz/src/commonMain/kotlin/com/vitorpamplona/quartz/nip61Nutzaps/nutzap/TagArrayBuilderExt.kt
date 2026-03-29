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
package com.vitorpamplona.quartz.nip61Nutzaps.nutzap

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.tags.events.toETagArray
import com.vitorpamplona.quartz.nip01Core.tags.kinds.KindTag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.tags.MintUrlTag
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.tags.ProofTag
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.tags.UnitTag

fun TagArrayBuilder<NutzapEvent>.proofs(proofs: List<String>) = addAll(ProofTag.assemble(proofs))

fun TagArrayBuilder<NutzapEvent>.mintUrl(url: String) = addUnique(MintUrlTag.assemble(url))

fun TagArrayBuilder<NutzapEvent>.unit(unit: String) = addUnique(UnitTag.assemble(unit))

fun TagArrayBuilder<NutzapEvent>.zappedEvent(eventHint: EventHintBundle<out Event>) = add(eventHint.toETagArray())

fun TagArrayBuilder<NutzapEvent>.zappedEventKind(kind: Int) = addUnique(KindTag.assemble(kind))

fun TagArrayBuilder<NutzapEvent>.recipient(pubKey: HexKey) = add(PTag.assemble(pubKey, null))

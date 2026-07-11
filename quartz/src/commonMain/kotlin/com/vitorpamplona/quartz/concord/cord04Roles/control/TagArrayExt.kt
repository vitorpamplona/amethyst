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
package com.vitorpamplona.quartz.concord.cord04Roles.control

import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityCitation
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.concord.cord04Roles.control.tags.EidTag
import com.vitorpamplona.quartz.concord.cord04Roles.control.tags.EpTag
import com.vitorpamplona.quartz.concord.cord04Roles.control.tags.EvTag
import com.vitorpamplona.quartz.concord.cord04Roles.control.tags.VacTag
import com.vitorpamplona.quartz.concord.cord04Roles.control.tags.VskTag
import com.vitorpamplona.quartz.nip01Core.core.TagArray

/** The Control Plane entity kind (`vsk`) this edition updates, or null if absent/unknown. */
fun TagArray.vsk(): ControlEntityKind? = firstNotNullOfOrNull(VskTag::parse)

/** The 32-byte entity id (`eid`), or null if absent/malformed. */
fun TagArray.eid(): ByteArray? = firstNotNullOfOrNull(EidTag::parse)

/** The edition version (`ev`), or null if absent/malformed/negative. */
fun TagArray.ev(): Long? = firstNotNullOfOrNull(EvTag::parse)

/** The previous-edition hash (`ep`), or null if absent (genesis) — see also the presence check in `fromRumor`. */
fun TagArray.ep(): ByteArray? = firstNotNullOfOrNull(EpTag::parse)

/** The authority citation (`vac`), or null if absent (owner-authored). */
fun TagArray.vac(): AuthorityCitation? = firstNotNullOfOrNull(VacTag::parse)

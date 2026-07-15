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
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder

fun TagArrayBuilder<ControlEditionEvent>.vsk(kind: ControlEntityKind) = addUnique(VskTag.assemble(kind))

fun TagArrayBuilder<ControlEditionEvent>.eid(entityId: ByteArray) = addUnique(EidTag.assemble(entityId))

fun TagArrayBuilder<ControlEditionEvent>.ev(version: Long) = addUnique(EvTag.assemble(version))

fun TagArrayBuilder<ControlEditionEvent>.ep(prevHash: ByteArray) = addUnique(EpTag.assemble(prevHash))

fun TagArrayBuilder<ControlEditionEvent>.vac(citation: AuthorityCitation) = addUnique(VacTag.assemble(citation))

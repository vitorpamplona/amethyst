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
package com.vitorpamplona.quartz.nip43RelayMembers.roles

import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip43RelayMembers.roles.tags.RoleColorTag
import com.vitorpamplona.quartz.nip43RelayMembers.roles.tags.RoleDescriptionTag
import com.vitorpamplona.quartz.nip43RelayMembers.roles.tags.RoleLabelTag
import com.vitorpamplona.quartz.nip43RelayMembers.roles.tags.RoleOrderTag

fun TagArrayBuilder<RelayRoleEvent>.roleLabel(label: String) = addUnique(RoleLabelTag.assemble(label))

fun TagArrayBuilder<RelayRoleEvent>.roleDescription(description: String) = addUnique(RoleDescriptionTag.assemble(description))

fun TagArrayBuilder<RelayRoleEvent>.roleColor(hue: Int) = addUnique(RoleColorTag.assemble(hue))

fun TagArrayBuilder<RelayRoleEvent>.roleOrder(order: Int) = addUnique(RoleOrderTag.assemble(order))

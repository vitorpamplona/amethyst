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
package com.vitorpamplona.quartz.buzz.mpProjects

import com.vitorpamplona.quartz.buzz.mpProjects.tags.ChannelTag
import com.vitorpamplona.quartz.buzz.mpProjects.tags.ProjectMember
import com.vitorpamplona.quartz.buzz.mpProjects.tags.ProjectMemberTag
import com.vitorpamplona.quartz.buzz.mpProjects.tags.ProjectVisibility
import com.vitorpamplona.quartz.buzz.mpProjects.tags.VisibilityTag
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip34Git.repository.tags.DescriptionTag
import com.vitorpamplona.quartz.nip34Git.repository.tags.NameTag

fun TagArrayBuilder<ProjectEvent>.projectName(name: String) = addUnique(NameTag.assemble(name))

fun TagArrayBuilder<ProjectEvent>.projectDescription(description: String) = addUnique(DescriptionTag.assemble(description))

/** One `a` tag per member. Duplicate coordinates are not merged — the relay rejects them. */
fun TagArrayBuilder<ProjectEvent>.projectMembers(members: List<ProjectMember>) = members.forEach { add(ProjectMemberTag.assemble(it)) }

fun TagArrayBuilder<ProjectEvent>.projectChannel(channelId: String) = addUnique(ChannelTag.assemble(channelId))

fun TagArrayBuilder<ProjectEvent>.projectVisibility(visibility: ProjectVisibility) = addUnique(VisibilityTag.assemble(visibility))

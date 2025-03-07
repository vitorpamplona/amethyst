/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip72ModCommunities.definition

import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip23LongContent.tags.ImageTag
import com.vitorpamplona.quartz.nip72ModCommunities.definition.tags.DescriptionTag
import com.vitorpamplona.quartz.nip72ModCommunities.definition.tags.ModeratorTag
import com.vitorpamplona.quartz.nip72ModCommunities.definition.tags.NameTag
import com.vitorpamplona.quartz.nip72ModCommunities.definition.tags.RelayTag
import com.vitorpamplona.quartz.nip72ModCommunities.definition.tags.RulesTag

fun TagArrayBuilder<CommunityDefinitionEvent>.name(name: String) = addUnique(NameTag.assemble(name))

fun TagArrayBuilder<CommunityDefinitionEvent>.description(description: String) = addUnique(DescriptionTag.assemble(description))

fun TagArrayBuilder<CommunityDefinitionEvent>.image(webUrl: String) = addUnique(ImageTag.assemble(webUrl))

fun TagArrayBuilder<CommunityDefinitionEvent>.rules(rules: String) = addUnique(RulesTag.assemble(rules))

fun TagArrayBuilder<CommunityDefinitionEvent>.moderators(mods: List<ModeratorTag>) = addAll(mods.map { it.toTagArray() })

fun TagArrayBuilder<CommunityDefinitionEvent>.relays(relays: List<RelayTag>) = addAll(relays.map { it.toTagArray() })

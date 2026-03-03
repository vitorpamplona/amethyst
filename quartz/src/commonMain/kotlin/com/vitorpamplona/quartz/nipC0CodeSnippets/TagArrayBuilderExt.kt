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
package com.vitorpamplona.quartz.nipC0CodeSnippets

import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nipC0CodeSnippets.tags.DepTag
import com.vitorpamplona.quartz.nipC0CodeSnippets.tags.ExtensionTag
import com.vitorpamplona.quartz.nipC0CodeSnippets.tags.LanguageTag
import com.vitorpamplona.quartz.nipC0CodeSnippets.tags.LicenseTag
import com.vitorpamplona.quartz.nipC0CodeSnippets.tags.RepoTag
import com.vitorpamplona.quartz.nipC0CodeSnippets.tags.RuntimeTag
import com.vitorpamplona.quartz.nipC0CodeSnippets.tags.SnippetDescriptionTag
import com.vitorpamplona.quartz.nipC0CodeSnippets.tags.SnippetNameTag

fun TagArrayBuilder<CodeSnippetEvent>.language(language: String) = addUnique(LanguageTag.assemble(language))

fun TagArrayBuilder<CodeSnippetEvent>.extension(extension: String) = addUnique(ExtensionTag.assemble(extension))

fun TagArrayBuilder<CodeSnippetEvent>.snippetName(name: String) = addUnique(SnippetNameTag.assemble(name))

fun TagArrayBuilder<CodeSnippetEvent>.snippetDescription(description: String) = addUnique(SnippetDescriptionTag.assemble(description))

fun TagArrayBuilder<CodeSnippetEvent>.runtime(runtime: String) = addUnique(RuntimeTag.assemble(runtime))

fun TagArrayBuilder<CodeSnippetEvent>.license(license: String) = addUnique(LicenseTag.assemble(license))

fun TagArrayBuilder<CodeSnippetEvent>.deps(deps: List<String>) = addAll(DepTag.assemble(deps))

fun TagArrayBuilder<CodeSnippetEvent>.repo(repo: String) = addUnique(RepoTag.assemble(repo))

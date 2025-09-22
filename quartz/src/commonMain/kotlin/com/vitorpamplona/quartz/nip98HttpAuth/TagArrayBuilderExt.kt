/**
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
package com.vitorpamplona.quartz.nip98HttpAuth

import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip98HttpAuth.tags.MethodTag
import com.vitorpamplona.quartz.nip98HttpAuth.tags.PayloadHashTag
import com.vitorpamplona.quartz.nip98HttpAuth.tags.UrlTag

fun TagArrayBuilder<HTTPAuthorizationEvent>.url(url: String) = addUnique(UrlTag.assemble(url))

fun TagArrayBuilder<HTTPAuthorizationEvent>.method(method: String) = addUnique(MethodTag.assemble(method))

fun TagArrayBuilder<HTTPAuthorizationEvent>.payloadHash(hash: String) = addUnique(PayloadHashTag.assemble(hash))

fun TagArrayBuilder<HTTPAuthorizationEvent>.payload(bytes: ByteArray) = addUnique(PayloadHashTag.assemble(bytes))

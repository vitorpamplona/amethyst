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
package com.vitorpamplona.quartz.nip01Core.jackson

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Applied to a reflectively-serialized DTO so Jackson OMITS a null field instead
 * of writing it.
 *
 * Optional protocol fields are absent, not null. A peer is free to type one
 * strictly: sending `"from": null` for an absent `from` earned
 * `Invalid list_transactions params: from must be an integer` from a NIP-47
 * wallet, and the request failed.
 *
 * A MIXIN rather than an annotation on the class, because these DTOs live in
 * `commonMain` and Jackson annotations are JVM-only. Class-level rather than the
 * mapper-wide `setSerializationInclusion`, which in Jackson 2.x also suppresses
 * null MAP ENTRIES — and [com.vitorpamplona.quartz.nip01Core.kotlinSerialization.anyToJsonElement]
 * deliberately keeps those, so a global setting would close one backend
 * divergence by opening another.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class OmitNullsMixin

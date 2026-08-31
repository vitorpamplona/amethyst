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
package com.vitorpamplona.quartz.nip47WalletConnect.jackson

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Applied to every NIP-47 request `params` class so Jackson OMITS a null field
 * instead of writing it.
 *
 * NIP-47 marks these parameters optional, and a wallet is free to type one
 * strictly: sending `"from": null` for an absent `from` earns
 * `Invalid list_transactions params: from must be an integer` from a wallet that
 * expects an integer or nothing, and the request fails.
 *
 * THE TWO BACKENDS DISAGREED, which is the reason this is a mixin rather than a
 * fix at one call site. [com.vitorpamplona.quartz.nip47WalletConnect.kotlinSerialization.Nip47RequestKSerializer]
 * builds every params object with `params.x?.let { put("x", it) }`, so the
 * kotlinx (native) backend has always omitted nulls; Jackson serializes the
 * params classes reflectively and wrote them. The same request was two different
 * documents depending on the platform.
 *
 * A MIXIN rather than an annotation on the class, because the params classes live
 * in `commonMain` and Jackson annotations are JVM-only.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class OmitNullParamsMixin

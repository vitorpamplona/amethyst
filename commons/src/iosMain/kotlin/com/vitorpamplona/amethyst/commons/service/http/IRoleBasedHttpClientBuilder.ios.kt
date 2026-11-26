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
package com.vitorpamplona.amethyst.commons.service.http

import io.ktor.client.HttpClient

/**
 * iOS's face of the per-role HTTP clients: one Ktor client per role and URL, the counterpart of
 * the JVM's `RoleBasedOkHttpClients`. Each role goes through Tor exactly when the same role does
 * on Android, because both ask the same `TorRoleRouting`; see [RoleBasedKtorClients].
 */
actual interface IRoleBasedHttpClientBuilder {
    /** The Ktor client a link preview of [url] goes out on. */
    fun ktorClientForPreview(url: String): HttpClient

    /** The Ktor client an image (or a cart, which is an image) at [url] goes out on. */
    fun ktorClientForImage(url: String): HttpClient

    /** The Ktor client the bytes of the video at [url] go out on. */
    fun ktorClientForVideo(url: String): HttpClient

    /** The Ktor client a money operation (LNURL, a Cashu mint, a block explorer) at [url] goes out on. */
    fun ktorClientForMoney(url: String): HttpClient

    /** The Ktor client a NIP-05 lookup of [url] goes out on. */
    fun ktorClientForNip05(url: String): HttpClient

    /** The Ktor client an upload to [url] goes out on. */
    fun ktorClientForUploads(url: String): HttpClient

    /** The Ktor client a push registration at [url] goes out on. */
    fun ktorClientForPushRegistration(url: String): HttpClient

    /** Whether the video at [url] must go through Tor (some players cannot, and must refuse it). */
    fun shouldUseTorForVideo(url: String): Boolean
}

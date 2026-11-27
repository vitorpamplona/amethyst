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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.cordn.client.CordnBlobStore
import com.vitorpamplona.cordn.client.CordnBlossomHttp
import com.vitorpamplona.cordn.client.CordnTipBlobStore

/**
 * Where a migration's sealed documents are stored: cordn-kmp's [CordnTipBlobStore], which holds
 * the §12 rules (a throwaway signer for every upload, opaque bytes, servers tried in order), over
 * the app's Blossom client ([http]: OkHttp's on the JVM, Ktor's elsewhere) and so over the network
 * route the user picked for uploads.
 *
 * Not the attachment path. Message attachments are signed by the account
 * (`account.createBlossomUploadAuth`, see `CordnMediaService`) because they already ride inside a
 * group; the point of a migration blob is that nothing links it to anyone.
 */
class BlossomCordnBlobStore(
    servers: List<String>,
    http: CordnBlossomHttp,
) : CordnBlobStore by CordnTipBlobStore(servers, http)

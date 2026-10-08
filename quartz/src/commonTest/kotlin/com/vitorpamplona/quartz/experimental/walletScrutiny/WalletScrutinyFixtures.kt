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
package com.vitorpamplona.quartz.experimental.walletScrutiny

/**
 * Real WalletScrutiny events from the 2026-10-08 relay census. Both are infrastructure records (an
 * APK upload and the build server's verdict on it), with no personal data. The verification's `x`
 * tags are the bundle's file hashes.
 */
object WalletScrutinyFixtures {
    /** Kind 9401: Bitcoin Keeper 2.6.3 for Android, a base APK and three splits. */
    const val REAL_ASSET_BUNDLE = """{"id": "847920e85b24280e7f6a7c369d20035e28d478455bd1e8a2b9571529bbd251d1", "pubkey": "0cc927e6ddc73aa3146593bfce2809a4466fc2249914fb5a42b543a0d4539565", "created_at": 1791388652, "kind": 9401, "tags": [["x", "bc63fb293d075a929abc0f216a179756e009c1bdb71e9f4878bdf334be293eb5", "base.apk"], ["x", "11740257e813455501301d5dc0f9870dc476e15947d912523e051d9a3400ab8c", "split_config.arm64_v8a.apk"], ["x", "fa22d9be3c1077bda72eb2d5f26bf280ea3c8938498863489c2a766f8b6eda10", "split_config.es.apk"], ["x", "da3b93b89e98cff52bbea6c593fc26193139d53f400139ebd44ac51118f08195", "split_config.xxhdpi.apk"], ["i", "io.hexawallet.bitcoinkeeper"], ["version", "2.6.3"], ["platform", "android"], ["client", "WalletScrutiny.com", "31990:168b7a2cd8bb9205c3f574de540606d6f4c46717c5164f47373fdcce2b9cd335:7703371760017", "wss://relay.nostr.info/"], ["c", "walletscrutiny"]], "content": "installed from Play Store, uploaded by WalletScrutiny Android", "sig": "28826dd0e86be95b54ac5ef6d1931242975b1daa7f87ebde88f3bc2bfec9d92a9c57098c0a03417b3b9619239a70f02484102e1eed1f5bb04128c4be13394c8d"}"""

    /** Kind 30301: the build server's `ftbfs` verdict on [REAL_ASSET_BUNDLE]. */
    const val REAL_BUILD_VERIFICATION = """{"id": "7d9ef6b688f1df57ebbbe772a4983499f24ec975c4196837ed1806b295d702c8", "pubkey": "168b7a2cd8bb9205c3f574de540606d6f4c46717c5164f47373fdcce2b9cd335", "created_at": 1791406660, "kind": 30301, "tags": [["status", "ftbfs"], ["d", "io.hexawallet.bitcoinkeeper:2.6.3:android:be7e304ae334b79c2f445efcf07ac8b0c4a9e120fbd35e87b3d85b03db945fea"], ["i", "io.hexawallet.bitcoinkeeper"], ["version", "2.6.3"], ["platform", "android"], ["client", "WalletScrutiny.com", "31990:168b7a2cd8bb9205c3f574de540606d6f4c46717c5164f47373fdcce2b9cd335:7703371760017", "wss://relay.nostr.info/"], ["c", "walletscrutiny"], ["x", "bc63fb293d075a929abc0f216a179756e009c1bdb71e9f4878bdf334be293eb5"], ["x", "11740257e813455501301d5dc0f9870dc476e15947d912523e051d9a3400ab8c"], ["x", "fa22d9be3c1077bda72eb2d5f26bf280ea3c8938498863489c2a766f8b6eda10"], ["x", "da3b93b89e98cff52bbea6c593fc26193139d53f400139ebd44ac51118f08195"], ["file-attachment", "f9fb84b7506cdbace4e2f4261358798e76e26767c784a3ead1a18f7c3d2ab741"], ["output-file", "io.hexawallet.bitcoinkeeper_bc63fb293d075a929abc0f216a179756e009c1bdb71e9f4878bdf334be293eb5_script.cast", "e4fd671d7946ce912663e6c6ec82200a3578519722bf50909293253dfeacd2fa"], ["based-on", "0adde8c9878ff0a9e50fac04b4e730f8eb21eb34ff57e185b9a9cd4259b005e9:1f9e547c2f31942623b8ad1d07713282e8640fd8cf474e9f79f18ace8af216ed"]], "content": "{\"description\":\"Automatic verification by WalletScrutiny Build Server\",\"content\":\"**Failed to build.** We could not build version 2.6.3 from its public source code.\\n\\n**Official files (SHA-256):**\\n- `bc63fb293d075a929abc0f216a179756e009c1bdb71e9f4878bdf334be293eb5`\\n- `11740257e813455501301d5dc0f9870dc476e15947d912523e051d9a3400ab8c`\\n- `fa22d9be3c1077bda72eb2d5f26bf280ea3c8938498863489c2a766f8b6eda10`\\n- `da3b93b89e98cff52bbea6c593fc26193139d53f400139ebd44ac51118f08195`\\n\\n<details>\\n<summary>Other information</summary>\\n\\n- Build script from verification `0adde8c9878ff0a9e50fac04b4e730f8eb21eb34ff57e185b9a9cd4259b005e9` by `1f9e547c2f31942623b8ad1d07713282e8640fd8cf474e9f79f18ace8af216ed`\\n- Script version: v0.1.2\\n- Command: `io.hexawallet.bitcoinkeeper_bc63fb293d075a929abc0f216a179756e009c1bdb71e9f4878bdf334be293eb5_script.sh --binary binary`\\n\\n**Notes from the script**\\n\\nSource build failed for io.hexawallet.bitcoinkeeper 2.6.3 (container exit 3). Official base APK SHA-256: bc63fb293d075a929abc0f216a179756e009c1bdb71e9f4878bdf334be293eb5.\\n\\n</details>\\n\"}", "sig": "658e711da8bf540bd6efe5b15ecc7624ab3a17ed9f3964b7bd0666dbb1d63ba4765ade03add22278619f9378fb7b7a389c7d1b6d28e025d3fea87278dac37d6a"}"""
}

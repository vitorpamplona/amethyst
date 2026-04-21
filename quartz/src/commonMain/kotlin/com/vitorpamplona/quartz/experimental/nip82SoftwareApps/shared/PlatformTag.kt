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
package com.vitorpamplona.quartz.experimental.nip82SoftwareApps.shared

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * NIP-82 Appendix A platform identifiers, loosely based on `uname -sm`.
 * Publishers may also use custom values not listed here.
 */
object Platform {
    const val ANDROID_ARM64_V8A = "android-arm64-v8a"
    const val ANDROID_ARMEABI_V7A = "android-armeabi-v7a"
    const val ANDROID_X86 = "android-x86"
    const val ANDROID_X86_64 = "android-x86_64"
    const val DARWIN_ARM64 = "darwin-arm64"
    const val DARWIN_X86_64 = "darwin-x86_64"
    const val LINUX_AARCH64 = "linux-aarch64"
    const val LINUX_X86_64 = "linux-x86_64"
    const val WINDOWS_AARCH64 = "windows-aarch64"
    const val WINDOWS_X86_64 = "windows-x86_64"
    const val IOS_ARM64 = "ios-arm64"
    const val FREEBSD_X86_64 = "freebsd-x86_64"
    const val FREEBSD_AARCH64 = "freebsd-aarch64"
    const val LINUX_ARMV7L = "linux-armv7l"
    const val LINUX_RISCV64 = "linux-riscv64"
    const val WASM32 = "wasm32"
    const val WASM64 = "wasm64"
    const val WASI_WASM32 = "wasi-wasm32"
    const val WASI_WASM64 = "wasi-wasm64"
}

/**
 * NIP-82: `f` tag — platform identifier restricting compatibility when the MIME type
 * alone does not fully determine the target platform. See [Platform] for known
 * identifiers and NIP-82 Appendix A.
 */
class PlatformTag {
    companion object {
        const val TAG_NAME = "f"

        fun parse(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return tag[1]
        }

        fun assemble(platform: String) = arrayOf(TAG_NAME, platform)

        fun assemble(platforms: List<String>) = platforms.map { assemble(it) }
    }
}

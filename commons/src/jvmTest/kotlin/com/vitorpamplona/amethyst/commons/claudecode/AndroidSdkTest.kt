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
package com.vitorpamplona.amethyst.commons.claudecode

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidSdkTest {
    private lateinit var home: File

    @BeforeTest
    fun setUp() {
        home = Files.createTempDirectory("home").toFile()
    }

    @AfterTest
    fun tearDown() {
        home.deleteRecursively()
    }

    private fun sdk(path: String) = File(home, path).also { check(it.mkdirs()) }

    @Test
    fun findsAndroidStudiosSdkWhenTheAppHasNoAndroidHome() {
        val mac = sdk("Library/Android/sdk")
        assertEquals(mapOf("ANDROID_HOME" to mac.path), AndroidSdk.environment(emptyMap(), home.path, "Mac OS X"))
        val linux = sdk("Android/Sdk")
        assertEquals(mapOf("ANDROID_HOME" to linux.path), AndroidSdk.environment(emptyMap(), home.path, "Linux"))
    }

    @Test
    fun keepsTheSdkTheEnvironmentAlreadyNames() {
        sdk("Library/Android/sdk")
        val own = sdk("my-sdk")
        assertEquals(emptyMap(), AndroidSdk.environment(mapOf("ANDROID_HOME" to own.path), home.path, "Mac OS X"))
        assertEquals(emptyMap(), AndroidSdk.environment(mapOf("ANDROID_SDK_ROOT" to own.path), home.path, "Mac OS X"))
    }

    @Test
    fun aVariableNamingNoFolderDoesNotHideTheInstalledSdk() {
        val mac = sdk("Library/Android/sdk")
        assertEquals(mapOf("ANDROID_HOME" to mac.path), AndroidSdk.environment(mapOf("ANDROID_HOME" to "/nowhere"), home.path, "Mac OS X"))
    }

    @Test
    fun aStaleSdkRootFollowsTheSdkFound() {
        // AGP 9.5 skips a variable naming no folder; older ones, in other repositories, compare the two.
        val mac = sdk("Library/Android/sdk")
        assertEquals(
            mapOf("ANDROID_HOME" to mac.path, "ANDROID_SDK_ROOT" to mac.path),
            AndroidSdk.environment(mapOf("ANDROID_SDK_ROOT" to "/nowhere"), home.path, "Mac OS X"),
        )
    }

    @Test
    fun setsNothingWithoutAnSdk() {
        assertEquals(emptyMap(), AndroidSdk.environment(emptyMap(), home.path, "Mac OS X"))
    }

    @Test
    fun looksInLocalAppDataOnWindows() {
        val appData = File(home, "AppData/Local")
        assertEquals(listOf("${appData.path}\\Android\\Sdk", "${home.path}\\AppData\\Local\\Android\\Sdk"), AndroidSdk.candidates(mapOf("LOCALAPPDATA" to appData.path), home.path, "Windows 11"))
    }
}

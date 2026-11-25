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
package com.vitorpamplona.amethyst.commons.androiddevices

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidAppTargetsTest {
    @Test
    fun findsThisRepositorysAndroidApp() {
        // The tests run in commons/: the checkout is its parent.
        val root = File("..").absoluteFile.normalize()
        val targets = AndroidAppTargets.find(root)
        val amethyst = targets.first { it.module == ":amethyst" }
        assertEquals(listOf("playDebug", "fdroidDebug"), amethyst.variants)
        // Libraries and the desktop app aren't Android apps.
        assertTrue(targets.none { it.module == ":commons" || it.module == ":desktopApp" }, targets.toString())
    }

    @Test
    fun readsSettingsIncludes() {
        val settings =
            """
            rootProject.name = "x"
            include(":app", ":lib")
            include ':legacy'
            // include(":commented")
            include("feature:home")
            """.trimIndent()
        assertEquals(listOf(":app", ":lib", ":legacy", ":feature:home"), AndroidAppTargets.modules(settings))
    }

    @Test
    fun variantsFromKotlinAndGroovyFlavors() {
        val kotlin =
            """
            android {
                flavorDimensions += "channel"
                productFlavors {
                    create("play") { dimension = "channel" }
                    create("fdroid") { dimension = "channel" }
                }
            }
            """.trimIndent()
        assertEquals(listOf("playDebug", "fdroidDebug"), AndroidAppTargets.variants(kotlin))
        val groovy =
            """
            android {
                flavorDimensions "tier"
                productFlavors {
                    free { dimension "tier" }
                    paid {
                        dimension "tier"
                        applicationIdSuffix ".paid"
                    }
                }
            }
            """.trimIndent()
        assertEquals(listOf("freeDebug", "paidDebug"), AndroidAppTargets.variants(groovy))
        assertEquals(listOf("debug"), AndroidAppTargets.variants("android { }"))
        assertTrue(AndroidAppTargets.isApp("plugins { alias(libs.plugins.androidApplication) }"))
        assertTrue(AndroidAppTargets.isApp("plugins { id(\"com.android.application\") }"))
    }
}

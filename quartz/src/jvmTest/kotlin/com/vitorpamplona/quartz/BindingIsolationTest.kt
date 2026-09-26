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
package com.vitorpamplona.quartz

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Marmot and cordn must be able to walk away from each other.
 *
 * They are two bindings of RFC 9420 onto two different delivery models, they do
 * not interoperate, and neither is a layer of the other. So a change to one must
 * never force a change to the other — which holds only while neither imports the
 * other, and while the engine under both imports neither.
 *
 * `mls/README.md` has stated the engine half of this as a grep a human is
 * supposed to run. A grep in a README is a wish. This is the same invariant as a
 * test, so the build says no instead of a reviewer noticing.
 *
 * ## Scope: shipped code only
 *
 * Main source sets, never tests, and deliberately: an interop test that drives
 * both profiles through one engine — `cordn/interop/BothCredentialProfilesTest`
 * is exactly that — is how we *demonstrate* the two cannot collide, and the
 * `mls/components` tests decode real Marmot payloads as fixtures because the
 * point of an interop test is to run against bytes that exist. A fixture is
 * data; an import in shipped code is a dependency.
 */
class BindingIsolationTest {
    private val quartzRoot: File by lazy {
        // Gradle runs tests with the module directory as the working directory,
        // but resolve it rather than trust it: a wrong root would make every
        // assertion below vacuously pass, which is the one outcome an
        // architecture test must never have.
        generateSequence(File(".").absoluteFile) { it.parentFile }
            .map { if (it.name == "quartz") it else File(it, "quartz") }
            .firstOrNull { File(it, "src/commonMain/kotlin/com/vitorpamplona/quartz").isDirectory }
            ?: fail("cannot locate the quartz module from ${File(".").absolutePath}")
    }

    /** Main source sets only — see the class KDoc. */
    private fun sourceSets(pkg: String): List<File> =
        File(quartzRoot, "src")
            .listFiles()
            .orEmpty()
            .filter { it.isDirectory && !it.name.endsWith("Test") }
            .map { File(it, "kotlin/com/vitorpamplona/quartz/$pkg") }
            .filter { it.isDirectory }

    /** Every `import <forbidden>` under [pkg], as `path:line  import…`. */
    private fun offendingImports(
        pkg: String,
        forbidden: String,
    ): List<String> =
        sourceSets(pkg).flatMap { dir ->
            dir
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file ->
                    file.readLines().withIndex().mapNotNull { (i, line) ->
                        if (line.trimStart().startsWith("import $forbidden")) {
                            "${file.relativeTo(quartzRoot)}:${i + 1}  ${line.trim()}"
                        } else {
                            null
                        }
                    }
                }
        }

    private fun assertNoImports(
        pkg: String,
        forbidden: String,
        why: String,
    ) {
        val offences = offendingImports(pkg, forbidden)
        assertTrue(
            offences.isEmpty(),
            "$pkg must not import $forbidden — $why\n  " + offences.joinToString("\n  "),
        )
    }

    @Test
    fun `the engine knows nothing about either binding`() {
        // The reason Stage 1 of the cordn interop plan existed at all. An engine
        // that imports a binding is an engine only that binding can use, and
        // :quic was already reaching into it for X25519 before the split.
        assertNoImports("mls", "com.vitorpamplona.quartz.marmot", "it is RFC 9420, not Marmot")
        assertNoImports("mls", "com.vitorpamplona.quartz.cordn", "it is RFC 9420, not cordn")
        assertNoImports("mls", "com.vitorpamplona.quartz.nip01Core", "it is RFC 9420, not Nostr")
    }

    @Test
    fun `cordn does not depend on Marmot`() {
        // Everything named Marmot*, Mip* or mip0* is the wrong layer for cordn:
        // a different delivery service, a different credential encoding, a
        // different capability profile. Reuse there would couple two protocols
        // that have no reason to move together.
        assertNoImports("cordn", "com.vitorpamplona.quartz.marmot", "they are independent bindings")
    }

    @Test
    fun `Marmot does not depend on cordn`() {
        // The direction that matters most in practice: Marmot ships to users
        // today, and cordn is the newer, less settled of the two. Marmot must
        // not acquire a reason to care when cordn changes.
        assertNoImports("marmot", "com.vitorpamplona.quartz.cordn", "they are independent bindings")
    }

    @Test
    fun `the check is actually looking at files`() {
        // Guards the failure mode that would make every assertion above pass
        // for the wrong reason: a bad root, a renamed package, an empty walk.
        listOf("mls", "marmot", "cordn").forEach { pkg ->
            val files =
                sourceSets(pkg).flatMap {
                    it.walkTopDown().filter { f -> f.isFile && f.extension == "kt" }.toList()
                }
            assertTrue(files.size > 5, "found only ${files.size} Kotlin files under $pkg — the scan is broken, not the code")
        }
        // And that it can see an import at all, using one every binding has.
        assertTrue(
            offendingImports("cordn", "com.vitorpamplona.quartz.mls").isNotEmpty(),
            "the import scanner found no mls imports in cordn, so it would not find a marmot one either",
        )
    }
}

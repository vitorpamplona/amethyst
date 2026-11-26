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
package com.vitorpamplona.iossimulator

import com.vitorpamplona.devicecore.crash.DeviceCrash
import com.vitorpamplona.devicecore.ui.Bounds
import com.vitorpamplona.iossimulator.ax.AxTree
import com.vitorpamplona.iossimulator.crash.IosCrashReport
import com.vitorpamplona.iossimulator.idb.IdbCrash
import com.vitorpamplona.iossimulator.xcode.XcodeAppTargets
import com.vitorpamplona.iossimulator.xcode.XcodeBuilder
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AxTreeTest {
    // The shape idb's nested format has: frames in points, children inside their parent.
    private val tree =
        """
        [{"type":"Application","AXLabel":"Notes","frame":{"x":0,"y":0,"width":402,"height":874},"enabled":true,"children":[
          {"type":"NavigationBar","AXLabel":"Inbox","frame":{"x":0,"y":62,"width":402,"height":54},"children":[
            {"type":"Button","AXLabel":"Back","AXUniqueId":"nav.back","frame":{"x":8,"y":70,"width":44,"height":44},"enabled":true,"traits":["Button"]}
          ]},
          {"type":"TextField","AXLabel":"Email","AXValue":null,"placeholder":"you@example.com","frame":{"x":20,"y":200,"width":362,"height":44},"enabled":true,"focused":true},
          {"type":"SecureTextField","AXLabel":"Password","AXValue":"••••","frame":{"x":20,"y":260,"width":362,"height":44}},
          {"type":"Switch","AXLabel":"Remember me","AXValue":"1","frame":{"x":320,"y":320,"width":51,"height":31}},
          {"type":"StaticText","AXLabel":"Hidden","frame":{"x":0,"y":0,"width":10,"height":10},"hidden":true},
          {"type":"Button","AXLabel":"Sign in","AXFrame":"{{20, 400}, {362, 50}}","traits":["Button","NotEnabled"]}
        ]}]
        """.trimIndent()

    @Test
    fun elementsBecomeNumberedNodesInPixels() {
        val parsed = AxTree.parse(tree, density = 3.0)
        assertEquals(listOf("Application", "NavigationBar", "Button", "TextField", "SecureTextField", "Switch", "Button"), parsed.nodes.map { it.className })
        val back = parsed.nodes[2]
        assertEquals("Back", back.text)
        assertEquals("nav.back", back.resourceId)
        assertEquals(Bounds(24, 210, 156, 342), back.bounds)
        assertTrue(back.clickable)
        assertEquals(2, back.depth)

        val email = parsed.nodes[3]
        assertTrue(email.isEditable)
        assertTrue(email.focused)
        // An empty field shows what it asks for.
        assertEquals("you@example.com", email.contentDescription)

        assertTrue(parsed.nodes[4].password)
        val remember = parsed.nodes[5]
        assertTrue(remember.checkable)
        assertTrue(remember.checked)

        // An old companion's frame string, and a button that is greyed out.
        val signIn = parsed.nodes[6]
        assertEquals(Bounds(60, 1200, 1146, 1350), signIn.bounds)
        assertFalse(signIn.enabled)
    }

    @Test
    fun anAgentFindsAndReadsTheScreenAsOnAndroid() {
        val parsed = AxTree.parse(tree, density = 3.0)
        assertEquals("Sign in", parsed.find("sign in")?.text)
        assertEquals(3, parsed.find("Email")?.id)
        val text = parsed.describe(scale = 1.0 / 3)
        assertTrue("#2 Button \"Back\" id=nav.back [8,70][52,114] clickable" in text, text)
        assertTrue("password" in text)
    }
}

class IosCrashReportTest {
    private val ips =
        """
        {"app_name":"Notes","timestamp":"2026-11-26 10:02:03.00 -0500","app_version":"1.0","bundleID":"com.example.notes","name":"Notes","os_version":"iPhone OS 26.0 (23A341)"}
        {
          "exception": {"codes":"0x0000000000000001, 0x00000001a2b3c4d5","type":"EXC_BREAKPOINT","signal":"SIGTRAP"},
          "asi": {"libswiftCore.dylib":["Notes/ContentView.swift:42: Fatal error: Index out of range"]},
          "faultingThread": 0,
          "threads": [{"triggered": true, "frames": [
            {"imageOffset": 4660, "symbol": "Swift._assertionFailure(_:_:file:line:flags:)", "symbolLocation": 172, "imageIndex": 1},
            {"imageOffset": 20480, "symbol": "ContentView.body.getter", "symbolLocation": 120, "imageIndex": 0},
            {"imageOffset": 8192, "imageIndex": 0}
          ]}],
          "usedImages": [{"name": "Notes"}, {"name": "libswiftCore.dylib"}]
        }
        """.trimIndent()

    @Test
    fun theReportSaysWhatStoppedTheAppAndWhere() {
        val crash = IosCrashReport.parse(IdbCrash("Notes-2026-11-26-100203.ips", "com.example.notes", "Notes", 4821, 1_795_000_000), ips)
        assertEquals(DeviceCrash.Kind.IOS, crash.kind)
        assertEquals("com.example.notes", crash.packageName)
        assertEquals("EXC_BREAKPOINT (SIGTRAP): Notes/ContentView.swift:42: Fatal error: Index out of range", crash.headline)
        assertEquals(
            listOf(
                "#0 libswiftCore.dylib  Swift._assertionFailure(_:_:file:line:flags:) + 172",
                "#1 Notes  ContentView.body.getter + 120",
                "#2 Notes  0x2000",
            ),
            crash.details,
        )
        assertTrue(crash.summary().startsWith("com.example.notes crashed at "))
    }

    @Test
    fun anOldTextReportStillSaysSomething() {
        val text = "Incident Identifier: 1234\nProcess: Notes [4821]\nException Type:  EXC_BAD_ACCESS (SIGSEGV)\n"
        val crash = IosCrashReport.parse(IdbCrash("Notes.crash", "com.example.notes", "Notes", 4821, 0), text)
        assertEquals("EXC_BAD_ACCESS (SIGSEGV)", crash.headline)
    }
}

class XcodeAppTargetsTest {
    @Test
    fun findsTheProjectsOfAFolderAndItsKotlinMultiplatformApp() {
        val root = Files.createTempDirectory("xcode-targets").toFile()
        try {
            File(root, "Notes.xcodeproj/xcshareddata/xcschemes").mkdirs()
            File(root, "Notes.xcodeproj/xcshareddata/xcschemes/Notes.xcscheme").writeText("<Scheme/>")
            File(root, "Notes.xcodeproj/xcshareddata/xcschemes/NotesUITests.xcscheme").writeText("<Scheme/>")
            // A KMP build: the Xcode project is under iosApp/, without shared schemes.
            File(root, "kmp/iosApp/iosApp.xcodeproj").mkdirs()
            File(root, "kmp/gradlew").writeText("")
            // CocoaPods' own project is never an app to run.
            File(root, "Pods/Pods.xcodeproj").mkdirs()
            val targets = XcodeAppTargets.find(root)
            assertEquals(listOf("Notes", "iosApp"), targets.map { it.label })
            assertEquals(listOf("Notes", "NotesUITests"), targets[0].schemes)
            assertEquals(listOf("iosApp"), targets[1].schemes)
            assertFalse(targets[0].isWorkspace)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun aWorkspaceIsPreferredOverItsProject() {
        val root = Files.createTempDirectory("xcode-ws").toFile()
        try {
            File(root, "App.xcodeproj").mkdirs()
            File(root, "App.xcworkspace").mkdirs()
            val target = XcodeAppTargets.find(root).single()
            assertTrue(target.isWorkspace)
            assertEquals(listOf("App"), target.schemes)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun theBundleIdOfAnXmlPlist() {
        val plist = "<plist><dict><key>CFBundleName</key><string>Notes</string><key>CFBundleIdentifier</key>\n  <string>com.example.notes</string></dict></plist>"
        assertEquals("com.example.notes", XcodeBuilder.xmlBundleId(plist))
    }

    @Test
    fun theAppTheBuildWrote() {
        val products = Files.createTempDirectory("products").toFile()
        try {
            File(products, "Debug-iphonesimulator/Other.app").mkdirs()
            File(products, "Debug-iphonesimulator/Notes.app").mkdirs()
            File(products, "Debug-iphoneos/Notes.app").mkdirs()
            val app = assertNotNull(XcodeBuilder(File("/usr/bin/xcrun"), FakeRunner { FakeRunner.ok() }).builtApp(products, "Notes"))
            assertEquals("Debug-iphonesimulator", app.parentFile.name)
        } finally {
            products.deleteRecursively()
        }
    }
}

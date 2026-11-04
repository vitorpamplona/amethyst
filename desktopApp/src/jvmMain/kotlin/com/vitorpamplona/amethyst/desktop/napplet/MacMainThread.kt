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
package com.vitorpamplona.amethyst.desktop.napplet

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import javax.swing.SwingUtilities

/** Runs work on the macOS AppKit main thread, where some of JCEF's native steps must happen. */
internal object MacMainThread {
    @Suppress("FunctionName")
    private interface LibSystem : Library {
        fun dispatch_sync_f(
            queue: Pointer,
            context: Pointer?,
            work: DispatchFunction,
        )
    }

    private interface DispatchFunction : Callback {
        fun invoke(context: Pointer?)
    }

    /** Runs [block] on the AppKit main thread and waits for it. Never call it from the AWT thread. */
    fun run(block: () -> Unit) {
        val system = Native.load("System", LibSystem::class.java)
        val mainQueue = NativeLibrary.getInstance("System").getGlobalVariableAddress("_dispatch_main_q")
        var result: Result<Unit>? = null
        val work =
            object : DispatchFunction {
                override fun invoke(context: Pointer?) {
                    result = runCatching(block)
                }
            }
        system.dispatch_sync_f(mainQueue, null, work)
        (result ?: error("The main thread did not run the block")).getOrThrow()
    }

    /**
     * Loads JCEF's native library on the main thread, before jcefmaven loads it from whichever thread
     * starts Chromium. The library's Objective-C `+load` installs a mouse and scroll event monitor
     * that copies a C++ static set, and macOS runs `+load` before the library's C++ constructors: a
     * click the main thread dispatches in between copies a set that doesn't exist yet and crashes
     * the app (`std::set::insert` under `NSApplication(JCEFApplication) load`). The main thread
     * can't dispatch an event while it is busy loading the library, so the gap is never seen.
     * jcefmaven's own load then finds the library already loaded.
     */
    fun loadJcef(installDir: File) {
        val library = File(installDir, "libjcef.dylib")
        if (library.isFile) run { System.load(library.absolutePath) }
    }

    // --- Running after the main thread has done what the AWT thread queued for it ---

    @Suppress("FunctionName")
    private interface ObjC : Library {
        fun objc_getClass(name: String): Pointer?

        fun objc_allocateClassPair(
            superclass: Pointer,
            name: String,
            extraBytes: Long,
        ): Pointer?

        fun objc_registerClassPair(cls: Pointer)

        fun class_addMethod(
            cls: Pointer,
            selector: Pointer,
            imp: Callback,
            types: String,
        ): Boolean

        fun sel_registerName(name: String): Pointer

        fun objc_msgSend(
            receiver: Pointer,
            selector: Pointer,
        ): Pointer

        // -performSelectorOnMainThread:withObject:waitUntilDone:
        fun objc_msgSend(
            receiver: Pointer,
            selector: Pointer,
            performed: Pointer,
            argument: Pointer?,
            waitUntilDone: Boolean,
        ): Pointer?
    }

    private interface MarkerImp : Callback {
        fun invoke(
            self: Pointer,
            selector: Pointer,
            argument: Pointer?,
        )
    }

    private val afterQueued = ConcurrentLinkedQueue<() -> Unit>()

    // Held for the life of the process: the class's method calls into it.
    private val markerImp =
        object : MarkerImp {
            override fun invoke(
                self: Pointer,
                selector: Pointer,
                argument: Pointer?,
            ) {
                afterQueued.poll()?.let { SwingUtilities.invokeLater(it) }
            }
        }

    private class Marker(
        val objc: ObjC,
        val instance: Pointer,
        val perform: Pointer,
        val fire: Pointer,
    )

    private val marker: Marker by lazy {
        val objc = Native.load("objc", ObjC::class.java)
        val fire = objc.sel_registerName("amethystFire:")
        val cls: Pointer = objc.objc_getClass(MARKER_CLASS) ?: makeMarkerClass(objc, fire)
        val instance = objc.objc_msgSend(objc.objc_msgSend(cls, objc.sel_registerName("alloc")), objc.sel_registerName("init"))
        Marker(objc, instance, objc.sel_registerName("performSelectorOnMainThread:withObject:waitUntilDone:"), fire)
    }

    private fun makeMarkerClass(
        objc: ObjC,
        fire: Pointer,
    ): Pointer {
        val superclass = objc.objc_getClass("NSObject") ?: error("No NSObject")
        val made = objc.objc_allocateClassPair(superclass, MARKER_CLASS, 0L) ?: error("Could not make the marker class")
        check(objc.class_addMethod(made, fire, markerImp, "v@:@")) { "Could not add the marker's method" }
        objc.objc_registerClassPair(made)
        return made
    }

    private const val MARKER_CLASS = "AmethystMainThreadMarker"

    /**
     * Runs [block] on the AWT thread once the main thread has run everything the AWT thread queued
     * for it with `performSelectorOnMainThread` before this call. macOS runs those in the order one
     * thread queued them, and this queues one more, so it comes after JCEF's. Call it on the AWT
     * thread; it does not wait.
     */
    fun afterQueuedWork(block: () -> Unit) {
        val marker = marker
        afterQueued.add(block)
        marker.objc.objc_msgSend(marker.instance, marker.perform, marker.fire, null, false)
    }
}

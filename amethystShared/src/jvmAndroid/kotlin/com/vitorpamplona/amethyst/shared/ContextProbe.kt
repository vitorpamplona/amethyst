package com.vitorpamplona.amethyst.shared

import android.content.Context

/**
 * Phase 0 probe: proves a single source file in `jvmAndroid` can reference an
 * `android.*` type and compile for BOTH the Android and the JVM target.
 */
fun probePackageName(context: Context): String = context.packageName

---
title: "fix: macOS bundled VLC discovery broken by versioned setenv symbol"
type: fix
status: active
date: 2026-05-18
deepened: 2026-05-18
origin: docs/brainstorms/2026-05-18-fix-macos-vlc-bundling-brainstorm.md
---

# fix: macOS bundled VLC discovery broken by versioned setenv symbol

> **Status:** abandoned — Superseded — VLC/VLCJ was removed entirely (kdroidFilter migration); MacOsVlcDiscoverer no longer exists, so the setenv fix is moot.
> _Audited 2026-06-30._


## Enhancement Summary

**Deepened on:** 2026-05-18
**Research agents used:** VLC --plugin-path audit, macOS JNA symbol research, VlcjPlayerPool init flow audit

### Key Improvements from Research
1. `--plugin-path` factory arg is unreliable — VLC needs `VLC_PLUGIN_PATH` env var set before `libvlc_new()`
2. `--reset-plugins-cache` is VLC CLI-only, not available via libvlc/vlcj factory args
3. Simpler fix: replace `LibC.INSTANCE.setenv()` with JNA-free env var setting

## Overview

Bundled VLC video playback is broken on macOS release builds.
`MacOsVlcDiscoverer.setPluginPath()` calls `LibC.INSTANCE.setenv()` via JNA,
but macOS 13+ uses versioned C symbols (`setenv$3b99ba0d`) that JNA's `dlsym`
cannot resolve. Without system VLC installed, video is completely unavailable.

(See brainstorm: `docs/brainstorms/2026-05-18-fix-macos-vlc-bundling-brainstorm.md`)

## Problem Statement

```
VLC: bundled discovery threw Error looking up function 'setenv$3b99ba0d':
  dlsym(0x..., setenv$3b99ba0d): symbol not found
VLC: init failed — ...DiscoveryDirectoryProvider: Provider ...not found
```

- Affects all macOS DMG users without system VLC.app installed
- Audio playback works (separate factory, no plugin path needed for audio codecs)
- Graceful degradation exists (shows "Install VLC" message) but shouldn't be needed

## Proposed Solution (Updated from Research)

**Replace `LibC.INSTANCE.setenv()` with a JNA-free approach to set the env var.**

VLC requires `VLC_PLUGIN_PATH` as a process-level environment variable BEFORE
`libvlc_new()` (called by `MediaPlayerFactory`). The `--plugin-path` factory arg
is NOT reliably forwarded to the plugin scanner. So we must set the actual env var,
just without using JNA's broken `LibC.setenv` binding.

### Approach: Use JNA's lower-level Native.getLibrary to call setenv directly

Instead of `LibC.INSTANCE.setenv()` which goes through vlcj's `LibC` interface
binding (which triggers the versioned symbol lookup), use JNA's `Function.getFunction`
to call `setenv` from libc directly without the problematic interface mapping:

```kotlin
override fun setPluginPath(pluginPath: String?): Boolean {
    if (pluginPath == null) return false
    return try {
        // Call setenv directly via JNA Function API, bypassing LibC interface
        // which fails on macOS 13+ due to versioned symbol lookup
        val setenv = com.sun.jna.Function.getFunction("c", "setenv")
        setenv.invokeInt(arrayOf(PLUGIN_ENV_NAME, pluginPath, 1)) == 0
    } catch (e: Throwable) {
        // Fallback: set as JVM system property for factory arg approach
        System.setProperty("vlc.plugin.path", pluginPath)
        true
    }
}
```

**If that still hits the symbol issue**, the alternative fallback is:

```kotlin
override fun setPluginPath(pluginPath: String?): Boolean {
    if (pluginPath == null) return false
    // Store for VlcjPlayerPool to pass as --plugin-path factory arg
    discoveredPluginPath = pluginPath
    return true
}
```

Then in `VlcjPlayerPool.init()`, pass `--plugin-path` to both factories as belt-and-suspenders.

### Plugin Cache Fix

Since `--reset-plugins-cache` is VLC CLI-only (not available via libvlc), fix stale
cache by deleting the cache file before factory creation:

```kotlin
// Delete stale VLC plugin cache before factory init
val cacheDir = File(System.getProperty("user.home"), "Library/Caches/org.videolan.vlc")
cacheDir.listFiles()?.filter { it.name.startsWith("plugins") }?.forEach { it.delete() }
```

## Implementation

### File 1: `MacOsVlcDiscoverer.kt`

**Current** (line 55):
```kotlin
override fun setPluginPath(pluginPath: String?): Boolean =
    LibC.INSTANCE.setenv(PLUGIN_ENV_NAME, pluginPath, 1) == 0
```

**Change to:**
```kotlin
var discoveredPluginPath: String? = null
    private set

override fun setPluginPath(pluginPath: String?): Boolean {
    if (pluginPath == null) return false
    discoveredPluginPath = pluginPath
    return try {
        // Direct JNA Function call bypasses vlcj's LibC interface binding
        // which fails on macOS 13+ (versioned symbol setenv$3b99ba0d)
        val setenv = com.sun.jna.Function.getFunction("c", "setenv")
        setenv.invokeInt(arrayOf(PLUGIN_ENV_NAME, pluginPath, 1)) == 0
    } catch (_: Throwable) {
        // If JNA call fails, store path for --plugin-path fallback
        false
    }
}
```

Remove import: `uk.co.caprica.vlcj.binding.lib.LibC`

### File 2: `VlcjPlayerPool.kt`

**Changes to `init()` (lines 77-113):**

1. Hold reference to `MacOsVlcDiscoverer` for plugin path access:
```kotlin
val macOsDiscoverer = MacOsVlcDiscoverer()
val nd = NativeDiscovery(BundledVlcDiscoverer(), macOsDiscoverer)
```

2. Build factory args with plugin path fallback:
```kotlin
val factoryArgs = mutableListOf("--no-xlib")

// If setenv failed, pass --plugin-path as fallback
val pluginPath = macOsDiscoverer.discoveredPluginPath
    ?: System.getProperty("vlc.plugin.path")
    ?: VlcResourceResolver.findVlcDir()?.let { "${it.absolutePath}/plugins" }

if (pluginPath != null && !envVarSetSuccessfully) {
    factoryArgs += "--plugin-path=$pluginPath"
}

val f = MediaPlayerFactory(*factoryArgs.toTypedArray())
```

3. Delete stale plugin cache before factory creation (macOS only):
```kotlin
if ("mac" in System.getProperty("os.name").lowercase()) {
    val cacheDir = File(System.getProperty("user.home"), "Library/Caches/org.videolan.vlc")
    cacheDir.listFiles()?.filter { it.name.startsWith("plugins") }?.forEach { it.delete() }
}
```

4. Audio factory also gets plugin path if env var wasn't set:
```kotlin
val audioArgs = mutableListOf("--no-video", "--no-xlib")
if (pluginPath != null && !envVarSetSuccessfully) {
    audioArgs += "--plugin-path=$pluginPath"
}
MediaPlayerFactory(*audioArgs.toTypedArray()).also { audioFactory = it }
```

### File 3: `desktopApp/build.gradle.kts`

Add JVM property as ultimate fallback:
```kotlin
jvmArgs += "-Dvlc.plugin.path=\$APPDIR/resources/vlc/plugins"
```

## Edge Cases from Research

| Edge Case | Handling |
|-----------|----------|
| `Function.getFunction("c", "setenv")` also fails on macOS | Caught by try/catch, falls back to `--plugin-path` factory arg |
| `--plugin-path` not honored by VLC 3.0.20 | JVM property `-Dvlc.plugin.path` as ultimate fallback |
| Audio factory created later without plugin path | Audio factory also receives `--plugin-path` if env var wasn't set |
| `findVlcDir()` returns root, not plugins dir | Append `/plugins` when building `--plugin-path` value |
| VLC plugin cache stale after VLC update | Cache deleted on startup before factory creation |
| NativeDiscovery swallows MacOsVlcDiscoverer ref | Restructured to hold ref before passing to NativeDiscovery |

## Acceptance Criteria

- [ ] Video plays in release DMG on macOS without system VLC installed
- [ ] No `setenv` symbol errors in console output
- [ ] No stale plugin cache warnings on launch
- [ ] Audio playback still works
- [ ] Linux/Windows builds unaffected
- [ ] `./gradlew :desktopApp:run` (debug) still works
- [ ] Fallback chain works: direct setenv → --plugin-path → JVM property

## Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| `Function.getFunction("c", "setenv")` may also hit versioned symbol | Medium | Triple fallback: direct call → --plugin-path → JVM property |
| `--plugin-path` ignored by some VLC builds | Low | Env var approach is primary, --plugin-path is fallback only |
| Plugin cache deletion too aggressive | Low | Only deletes `plugins*` files in VLC cache dir, not other data |

## Sources

- **Origin brainstorm:** [docs/brainstorms/2026-05-18-fix-macos-vlc-bundling-brainstorm.md](../brainstorms/2026-05-18-fix-macos-vlc-bundling-brainstorm.md)
- **Research:** VLC requires `VLC_PLUGIN_PATH` env var before `libvlc_new()` — `--plugin-path` not reliably forwarded
- **Research:** `--reset-plugins-cache` is CLI-only, not available via libvlc/vlcj
- **Research:** `setPluginPath()` called DURING `nd.discover()`, before factory creation
- **Research:** macOS 13+ versioned symbols affect JNA's `LibC` interface, but `Function.getFunction` may bypass it
- `MacOsVlcDiscoverer.kt:55` — failing `setenv` call
- `VlcjPlayerPool.kt:68-114` — init flow
- `VlcResourceResolver.kt` — returns VLC root dir (not plugins subdir)
- [JNA Issue #1423](https://github.com/java-native-access/jna/issues/1423) — macOS symbol resolution changes
- [Guardsquare/proguard#460](https://github.com/Guardsquare/proguard/issues/460) — related ProGuard bytecode rewriting

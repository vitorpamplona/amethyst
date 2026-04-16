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
package com.vitorpamplona.quartz.marmot.mip00KeyPackages

/**
 * Encrypted local storage for the [KeyPackageRotationManager]'s state.
 *
 * KeyPackageBundles contain private key material (init key, encryption key,
 * signature key) that the local user MUST retain on disk so they can process
 * Welcome events the inviter sends days or weeks after publishing the
 * KeyPackage. Without this storage, every app restart would discard the
 * bundles and the user would have to re-publish a fresh KeyPackage —
 * meanwhile, any inviter holding the old (now-orphaned) KeyPackage would be
 * unable to add them to a group, because the matching private key is gone.
 *
 * Implementations MUST encrypt all data at rest. The persisted blob is a
 * single opaque snapshot of the rotation manager state — both the active
 * bundles map and the pending-rotation set are serialized together so they
 * stay consistent across crashes.
 */
interface KeyPackageBundleStore {
    /**
     * Persist a snapshot of the rotation manager state.
     * Overwrites any previously saved state.
     *
     * @param snapshot opaque encoded bytes from
     *   [KeyPackageRotationManager.snapshotBytes]
     */
    suspend fun save(snapshot: ByteArray)

    /**
     * Load a previously saved snapshot, or null if none exists.
     */
    suspend fun load(): ByteArray?

    /**
     * Delete the persisted state (e.g., when wiping the account).
     */
    suspend fun delete()
}

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
package androidx.activity.compose

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * JVM stand-in for `rememberLauncherForActivityResult`.
 *
 * Android's launcher is asynchronous — it hands the result to `onResult` once
 * the other activity finishes. The desktop pickers behind these contracts are
 * modal and return inline, so the launcher runs the contract and forwards to
 * the same callback, which preserves the call-site shape exactly.
 */
typealias ManagedActivityResultLauncher<I, O> = ActivityResultLauncher<I, O>

@Composable
fun <I, O> rememberLauncherForActivityResult(
    contract: ActivityResultContracts.ActivityResultContract<I, O>,
    onResult: (O) -> Unit,
): ActivityResultLauncher<I, O> = remember(contract) { ActivityResultLauncher { input -> onResult(contract.launch(input)) } }

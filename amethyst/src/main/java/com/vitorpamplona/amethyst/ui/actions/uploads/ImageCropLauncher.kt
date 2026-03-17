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
package com.vitorpamplona.amethyst.ui.actions.uploads

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.yalantis.ucrop.UCrop
import java.io.File
import java.util.UUID

class CropImageContract : ActivityResultContract<CropImageInput, Uri?>() {
    override fun createIntent(
        context: Context,
        input: CropImageInput,
    ): Intent {
        val destinationFile = File(context.cacheDir, "cropped_${UUID.randomUUID()}.jpg")

        return UCrop
            .of(input.sourceUri, Uri.fromFile(destinationFile))
            .withMaxResultSize(4096, 4096)
            .getIntent(context)
    }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): Uri? =
        if (resultCode == Activity.RESULT_OK && intent != null) {
            UCrop.getOutput(intent)
        } else {
            null
        }
}

class CropImageInput(
    val sourceUri: Uri,
)

@Composable
fun ImageCropLauncher(
    sourceUri: Uri,
    onCropped: (Uri) -> Unit,
    onCancel: () -> Unit,
) {
    val launcher =
        rememberLauncherForActivityResult(
            contract = CropImageContract(),
            onResult = { resultUri ->
                if (resultUri != null) {
                    onCropped(resultUri)
                } else {
                    onCancel()
                }
            },
        )

    LaunchedEffect(sourceUri) {
        launcher.launch(CropImageInput(sourceUri))
    }
}

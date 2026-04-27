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
package com.vitorpamplona.amethyst.service.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.imagedescription.ImageDescriberOptions
import com.google.mlkit.genai.imagedescription.ImageDescription
import com.google.mlkit.genai.imagedescription.ImageDescriptionRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Unified alt-text suggestion service.
 *
 * Prefers Gemini-Nano-backed `genai-image-description` for full descriptive sentences when
 * the device supports AICore; falls back to the legacy keyword `image-labeling` model otherwise.
 */
class MLKitImageLabelService(
    private val context: Context,
) {
    private val labeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    }

    private val describer by lazy {
        runCatching {
            ImageDescription.getClient(
                ImageDescriberOptions.builder(context).build(),
            )
        }.getOrNull()
    }

    suspend fun labelImage(uri: Uri): List<Pair<String, Float>> =
        withContext(Dispatchers.IO) {
            try {
                val image = InputImage.fromFilePath(context, uri)
                suspendCancellableCoroutine { cont ->
                    labeler
                        .process(image)
                        .addOnSuccessListener { labels ->
                            cont.resume(labels.map { it.text to it.confidence })
                        }.addOnFailureListener {
                            cont.resume(emptyList())
                        }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    suspend fun suggestAltText(uri: Uri): String? = describeWithGenAi(uri) ?: labelKeywords(uri)

    private suspend fun describeWithGenAi(uri: Uri): String? =
        withContext(Dispatchers.IO) {
            val client = describer ?: return@withContext null
            try {
                val status = client.checkFeatureStatus().get()
                if (status != FeatureStatus.AVAILABLE) return@withContext null
                val bitmap = loadBitmap(uri) ?: return@withContext null
                val request = ImageDescriptionRequest.builder(bitmap).build()
                val description = client.runInference(request).get().description
                description?.trim()?.takeIf { it.isNotEmpty() }
            } catch (_: Exception) {
                null
            }
        }

    private suspend fun labelKeywords(uri: Uri): String? {
        val labels = labelImage(uri)
        val confident = labels.filter { it.second >= MIN_CONFIDENCE }.map { it.first }
        if (confident.isEmpty()) return null
        return confident.take(MAX_LABELS).joinToString(", ")
    }

    private fun loadBitmap(uri: Uri): Bitmap? =
        try {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            null
        }

    fun close() {
        labeler.close()
        describer?.close()
    }

    companion object {
        private const val MIN_CONFIDENCE = 0.6f
        private const val MAX_LABELS = 5
    }
}

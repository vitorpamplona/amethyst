package com.vitorpamplona.amethyst.ui.actions

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.service.HttpClient
import okhttp3.*
import okio.BufferedSource
import okio.IOException
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.util.UUID

object ImageSaver {
    /**
     * Saves the image to the gallery.
     * May require a storage permission.
     *
     * @see PICTURES_SUBDIRECTORY
     */
    fun saveImage(
        url: String,
        context: Context,
        onSuccess: () -> Any?,
        onError: (Throwable) -> Any?
    ) {
        val client = HttpClient.getHttpClient()

        val request = Request.Builder()
            .header("User-Agent", "Amethyst/${BuildConfig.VERSION_NAME}")
            .get()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                onError(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    check(response.isSuccessful)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val contentType = response.header("Content-Type")
                        checkNotNull(contentType) {
                            "Can't find out the content type"
                        }

                        saveContentQ(
                            displayName = File(url).nameWithoutExtension,
                            contentType = contentType,
                            contentSource = response.body.source(),
                            contentResolver = context.contentResolver
                        )
                    } else {
                        saveContentDefault(
                            fileName = File(url).name,
                            contentSource = response.body.source(),
                            context = context
                        )
                    }
                    onSuccess()
                } catch (e: Exception) {
                    e.printStackTrace()
                    onError(e)
                }
            }
        })
    }

    fun saveImage(
        localFile: File,
        mimeType: String?,
        context: Context,
        onSuccess: () -> Any?,
        onError: (Throwable) -> Any?
    ) {
        try {
            val extension = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: ""
            val buffer = localFile.inputStream().source().buffer()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveContentQ(
                    displayName = UUID.randomUUID().toString(),
                    contentType = mimeType ?: "",
                    contentSource = buffer,
                    contentResolver = context.contentResolver
                )
            } else {
                saveContentDefault(
                    fileName = UUID.randomUUID().toString() + ".$extension",
                    contentSource = buffer,
                    context = context
                )
            }
            onSuccess()
        } catch (e: Exception) {
            e.printStackTrace()
            onError(e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveContentQ(
        displayName: String,
        contentType: String,
        contentSource: BufferedSource,
        contentResolver: ContentResolver
    ) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, contentType)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + File.separatorChar + PICTURES_SUBDIRECTORY
            )
        }

        val masterUri = if (contentType.startsWith("image")) {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val uri =
            contentResolver.insert(masterUri, contentValues)
        checkNotNull(uri) {
            "Can't insert the new content"
        }

        try {
            val outputStream = contentResolver.openOutputStream(uri)
            checkNotNull(outputStream) {
                "Can't open the content output stream"
            }

            outputStream.use {
                contentSource.readAll(it.sink())
            }
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }
    }

    private fun saveContentDefault(
        fileName: String,
        contentSource: BufferedSource,
        context: Context
    ) {
        val subdirectory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            PICTURES_SUBDIRECTORY
        )

        if (!subdirectory.exists()) {
            subdirectory.mkdirs()
        }

        val outputFile = File(subdirectory, fileName)

        outputFile
            .outputStream()
            .use {
                contentSource.readAll(it.sink())
            }

        // Call the media scanner manually, so the image
        // appears in the gallery faster.
        MediaScannerConnection.scanFile(context, arrayOf(outputFile.toString()), null, null)
    }

    private const val PICTURES_SUBDIRECTORY = "Amethyst"
}

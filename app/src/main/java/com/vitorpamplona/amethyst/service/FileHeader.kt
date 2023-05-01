package com.vitorpamplona.amethyst.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.vitorpamplona.amethyst.model.toHexKey
import io.trbl.blurhash.BlurHash
import java.net.URL
import java.security.MessageDigest
import kotlin.math.roundToInt

class FileHeader(
    val url: String,
    val mimeType: String?,
    val hash: String,
    val size: Int,
    val blurHash: String?,
    val description: String? = null
) {
    companion object {
        fun prepare(fileUrl: String, mimeType: String?, description: String?, onReady: (FileHeader) -> Unit, onError: () -> Unit) {
            try {
                val imageData = URL(fileUrl).readBytes()

                prepare(imageData, fileUrl, mimeType, description, onReady, onError)
            } catch (e: Exception) {
                Log.e("ImageDownload", "Couldn't download image from server: ${e.message}")
                onError()
            }
        }

        fun prepare(data: ByteArray, fileUrl: String, mimeType: String?, description: String?, onReady: (FileHeader) -> Unit, onError: () -> Unit) {
            try {
                val sha256 = MessageDigest.getInstance("SHA-256")

                val hash = sha256.digest(data).toHexKey()
                val size = data.size

                val blurHash = if (mimeType?.startsWith("image/") == true) {
                    val opt = BitmapFactory.Options()
                    opt.inPreferredConfig = Bitmap.Config.ARGB_8888
                    val mBitmap = BitmapFactory.decodeByteArray(data, 0, data.size, opt)

                    val intArray = IntArray(mBitmap.width * mBitmap.height)
                    mBitmap.getPixels(
                        intArray,
                        0,
                        mBitmap.width,
                        0,
                        0,
                        mBitmap.width,
                        mBitmap.height
                    )

                    val aspectRatio = (mBitmap.width).toFloat() / (mBitmap.height).toFloat()

                    if (aspectRatio > 1) {
                        BlurHash.encode(intArray, mBitmap.width, mBitmap.height, 9, (9 * (1 / aspectRatio)).roundToInt())
                    } else if (aspectRatio < 1) {
                        BlurHash.encode(intArray, mBitmap.width, mBitmap.height, (9 * aspectRatio).roundToInt(), 9)
                    } else {
                        BlurHash.encode(intArray, mBitmap.width, mBitmap.height, 4, 4)
                    }
                } else {
                    null
                }

                onReady(FileHeader(fileUrl, mimeType, hash, size, blurHash, description))
            } catch (e: Exception) {
                Log.e("ImageDownload", "Couldn't convert image in to File Header: ${e.message}")
                onError()
            }
        }
    }
}

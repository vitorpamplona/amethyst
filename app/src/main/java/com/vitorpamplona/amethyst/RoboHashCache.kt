package com.vitorpamplona.amethyst

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import name.neuhalfen.projects.android.robohash.buckets.VariableSizeHashing
import name.neuhalfen.projects.android.robohash.handle.Handle
import name.neuhalfen.projects.android.robohash.handle.HandleFactory
import name.neuhalfen.projects.android.robohash.paths.Configuration
import name.neuhalfen.projects.android.robohash.repository.ImageRepository
import java.util.UUID

object RoboHashCache {

    lateinit var robots: MyRoboHash

    lateinit var defaultAvatar: ImageBitmap

    @Synchronized
    fun get(context: Context, hash: String): ImageBitmap {
        if (!this::robots.isInitialized) {
            robots = MyRoboHash(context)

            defaultAvatar = robots.imageForHandle(
                robots.calculateHandleFromUUID(
                    UUID.nameUUIDFromBytes("aaaa".toByteArray())
                )
            ).asImageBitmap()
        }

        return defaultAvatar
    }
}

/**
 * Recreates RoboHash to use a custom configuration
 */
class MyRoboHash(context: Context) {
    private val configuration: Configuration = ModifiedSet1Configuration()
    private val repository: ImageRepository
    private val hashing = VariableSizeHashing(configuration.bucketSizes)

    // Optional
    private var memoryCache: LruCache<String, Bitmap>? = null

    init {
        repository = ImageRepository(context.assets)
    }

    fun useCache(memoryCache: LruCache<String, Bitmap>?) {
        this.memoryCache = memoryCache
    }

    fun calculateHandleFromUUID(uuid: UUID?): Handle {
        val data = hashing.createBuckets(uuid)
        return handleFactory.calculateHandle(data)
    }

    fun imageForHandle(handle: Handle): Bitmap {
        if (null != memoryCache) {
            val cached = memoryCache!![handle.toString()]
            if (null != cached) return cached
        }
        val bucketValues = handle.bucketValues()
        val paths = configuration.convertToFacetParts(bucketValues)
        val sampleSize = 1
        val buffer = repository.createBuffer(configuration.width(), configuration.height())
        val target = buffer.copy(Bitmap.Config.ARGB_8888, true)
        val merged = Canvas(target)
        val paint = Paint(0)

        // The first image is not added as copy form the buffer
        for (i in paths.indices) {
            merged.drawBitmap(repository.getInto(buffer, paths[i], sampleSize), 0f, 0f, paint)
        }
        repository.returnBuffer(buffer)
        if (null != memoryCache) {
            memoryCache!!.put(handle.toString(), target)
        }
        return target
    }

    companion object {
        private val handleFactory = HandleFactory()
    }
}

/**
 * Custom configuration to avoid the use of String.format in the GeneratePath
 * This uses the default location and ends up encoding number in the local language
 */
class ModifiedSet1Configuration : Configuration {
    override fun convertToFacetParts(bucketValues: ByteArray): Array<String> {
        require(bucketValues.size == BUCKET_COUNT)
        val color = INT_TO_COLOR[bucketValues[BUCKET_COLOR].toInt()]
        val paths = mutableListOf<String>()

        // e.g.
        //   blue face  #2
        //   blue nose  #7
        //   blue
        val firstFacetBucket = BUCKET_COLOR + 1
        for (facet in 0 until FACET_COUNT) {
            val bucketValue = bucketValues[firstFacetBucket + facet].toInt()
            paths.add(generatePath(FACET_PATH_TEMPLATES[facet], color, bucketValue))
        }
        return paths.toTypedArray()
    }

    private fun generatePath(facetPathTemplate: String, color: String, bucketValue: Int): String {
        // TODO: Make more efficient
        return facetPathTemplate.replace("#ROOT#", ROOT).replace("#COLOR#".toRegex(), color)
            .replace("#ITEM#".toRegex(), (bucketValue + 1).toString().padStart(2, '0'))
    }

    override fun getBucketSizes(): ByteArray {
        return BUCKET_SIZES
    }

    override fun width(): Int {
        return 300
    }

    override fun height(): Int {
        return 300
    }

    companion object {
        private const val ROOT = "sets/set1"
        private const val BUCKET_COLOR = 0
        private const val COLOR_COUNT = 10
        private const val BODY_COUNT = 10
        private const val FACE_COUNT = 10
        private const val MOUTH_COUNT = 10
        private const val EYES_COUNT = 10
        private const val ACCESSORY_COUNT = 10
        private const val BUCKET_COUNT = 6
        private const val FACET_COUNT = 5
        private val BUCKET_SIZES = byteArrayOf(
            COLOR_COUNT.toByte(),
            BODY_COUNT.toByte(),
            FACE_COUNT.toByte(),
            MOUTH_COUNT.toByte(),
            EYES_COUNT.toByte(),
            ACCESSORY_COUNT.toByte()
        )
        private val INT_TO_COLOR = arrayOf(
            "blue",
            "brown",
            "green",
            "grey",
            "orange",
            "pink",
            "purple",
            "red",
            "white",
            "yellow"
        )
        private val FACET_PATH_TEMPLATES = arrayOf(
            "#ROOT#/#COLOR#/01Body/#COLOR#_body-#ITEM#.png",
            "#ROOT#/#COLOR#/02Face/#COLOR#_face-#ITEM#.png",
            "#ROOT#/#COLOR#/Mouth/#COLOR#_mouth-#ITEM#.png",
            "#ROOT#/#COLOR#/Eyes/#COLOR#_eyes-#ITEM#.png",
            "#ROOT#/#COLOR#/Accessory/#COLOR#_accessory-#ITEM#.png"
        )
    }
}

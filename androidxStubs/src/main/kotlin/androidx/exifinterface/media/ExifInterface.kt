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
package androidx.exifinterface.media

import java.io.File
import java.io.InputStream

/**
 * JVM stand-in for androidx.exifinterface.media.ExifInterface — deliberately
 * **fail-closed**, and deliberately not yet functional.
 *
 * Its only caller is `MetadataStripper`, which removes location, device and
 * timestamp EXIF from images before they are uploaded. That code reads tags to
 * decide whether an image is clean, and writes nulls plus `saveAttributes()`
 * to scrub it.
 *
 * A stub that quietly answered "no tags found" and made `saveAttributes()` a
 * no-op would be worse than useless here: `stripImageMetadata` would report
 * `stripped = true` for a file whose EXIF is entirely intact, and every desktop
 * upload would leak the photographer's GPS coordinates and camera serial. So
 * every entry point throws instead. `MetadataStripper` already catches and
 * returns `StrippingResult(uri, stripped = false)`, which is the honest answer:
 * on desktop, stripping has not happened.
 *
 * Replacing this with a real implementation — Apache commons-imaging, which
 * :desktopApp already bundles, can read and rewrite EXIF for JPEG and friends —
 * is a prerequisite for enabling image uploads in the desktop build. AVIF has
 * no commons-imaging support and must keep failing closed, exactly as the
 * Android path does.
 */
class ExifInterface {
    constructor(file: File) : this(file.absolutePath)

    constructor(filename: String) {
        unsupported()
    }

    constructor(stream: InputStream) {
        unsupported()
    }

    fun getAttribute(tag: String): String? = unsupported()

    fun setAttribute(
        tag: String,
        value: String?,
    ): Unit = unsupported()

    fun saveAttributes(): Unit = unsupported()

    fun getAttributeInt(
        tag: String,
        defaultValue: Int,
    ): Int = unsupported()

    fun getAttributeDouble(
        tag: String,
        defaultValue: Double,
    ): Double = unsupported()

    fun hasAttribute(tag: String): Boolean = unsupported()

    private fun unsupported(): Nothing =
        throw UnsupportedOperationException(
            "EXIF read/write is not implemented on the JVM target yet. Callers must treat this " +
                "as 'metadata was NOT stripped' and refuse to upload, never as 'the image is clean'.",
        )

    companion object {
        const val TAG_GPS_LATITUDE = "GPSLatitude"
        const val TAG_GPS_LATITUDE_REF = "GPSLatitudeRef"
        const val TAG_GPS_LONGITUDE = "GPSLongitude"
        const val TAG_GPS_LONGITUDE_REF = "GPSLongitudeRef"
        const val TAG_GPS_ALTITUDE = "GPSAltitude"
        const val TAG_GPS_ALTITUDE_REF = "GPSAltitudeRef"
        const val TAG_GPS_TIMESTAMP = "GPSTimeStamp"
        const val TAG_GPS_DATESTAMP = "GPSDateStamp"
        const val TAG_GPS_PROCESSING_METHOD = "GPSProcessingMethod"
        const val TAG_GPS_AREA_INFORMATION = "GPSAreaInformation"
        const val TAG_GPS_SPEED = "GPSSpeed"
        const val TAG_GPS_SPEED_REF = "GPSSpeedRef"
        const val TAG_GPS_TRACK = "GPSTrack"
        const val TAG_GPS_TRACK_REF = "GPSTrackRef"
        const val TAG_GPS_IMG_DIRECTION = "GPSImgDirection"
        const val TAG_GPS_IMG_DIRECTION_REF = "GPSImgDirectionRef"
        const val TAG_GPS_DEST_LATITUDE = "GPSDestLatitude"
        const val TAG_GPS_DEST_LATITUDE_REF = "GPSDestLatitudeRef"
        const val TAG_GPS_DEST_LONGITUDE = "GPSDestLongitude"
        const val TAG_GPS_DEST_LONGITUDE_REF = "GPSDestLongitudeRef"
        const val TAG_GPS_DEST_BEARING = "GPSDestBearing"
        const val TAG_GPS_DEST_BEARING_REF = "GPSDestBearingRef"
        const val TAG_GPS_DEST_DISTANCE = "GPSDestDistance"
        const val TAG_GPS_DEST_DISTANCE_REF = "GPSDestDistanceRef"
        const val TAG_GPS_MAP_DATUM = "GPSMapDatum"
        const val TAG_GPS_DOP = "GPSDOP"
        const val TAG_GPS_MEASURE_MODE = "GPSMeasureMode"
        const val TAG_GPS_SATELLITES = "GPSSatellites"
        const val TAG_GPS_STATUS = "GPSStatus"
        const val TAG_GPS_VERSION_ID = "GPSVersionID"
        const val TAG_IMAGE_UNIQUE_ID = "ImageUniqueID"
        const val TAG_DATETIME = "DateTime"
        const val TAG_DATETIME_ORIGINAL = "DateTimeOriginal"
        const val TAG_DATETIME_DIGITIZED = "DateTimeDigitized"
        const val TAG_OFFSET_TIME = "OffsetTime"
        const val TAG_OFFSET_TIME_ORIGINAL = "OffsetTimeOriginal"
        const val TAG_OFFSET_TIME_DIGITIZED = "OffsetTimeDigitized"
        const val TAG_MAKE = "Make"
        const val TAG_MODEL = "Model"
        const val TAG_SOFTWARE = "Software"
        const val TAG_ARTIST = "Artist"
        const val TAG_COPYRIGHT = "Copyright"
        const val TAG_USER_COMMENT = "UserComment"
        const val TAG_IMAGE_DESCRIPTION = "ImageDescription"
        const val TAG_LENS_MAKE = "LensMake"
        const val TAG_LENS_MODEL = "LensModel"
        const val TAG_LENS_SERIAL_NUMBER = "LensSerialNumber"
        const val TAG_BODY_SERIAL_NUMBER = "BodySerialNumber"
        const val TAG_CAMERA_OWNER_NAME = "CameraOwnerName"
        const val TAG_ORIENTATION = "Orientation"
        const val TAG_IMAGE_WIDTH = "ImageWidth"
        const val TAG_IMAGE_LENGTH = "ImageLength"
        const val ORIENTATION_NORMAL = 1
    }
}

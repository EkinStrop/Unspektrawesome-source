package com.unspektrawesome.capture

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class JpegMediaStoreWriter(
    private val contentResolver: ContentResolver,
) {
    fun write(
        image: ProcessedImage,
        metadata: CaptureMetadata,
        jpegQuality: Int = DEFAULT_JPEG_QUALITY,
        capturedAtMillis: Long = System.currentTimeMillis(),
    ): Uri {
        require(jpegQuality in 1..100) { "JPEG quality must be in 1..100" }
        val displayName = FILE_DATE_FORMAT.get().format(Date(capturedAtMillis)) + ".jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "$PICTURES_DIRECTORY/$ALBUM_NAME")
            put(MediaStore.Images.Media.DATE_TAKEN, capturedAtMillis)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = contentResolver.insert(collection, values)
            ?: throw IOException("MediaStore rejected the image")
        try {
            writePixels(uri, image, jpegQuality)
            writeExif(uri, metadata, capturedAtMillis)
            contentResolver.update(uri, ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }, null, null)
            return uri
        } catch (error: Throwable) {
            contentResolver.delete(uri, null, null)
            throw error
        }
    }

    private fun writePixels(uri: Uri, image: ProcessedImage, jpegQuality: Int) {
        val pixels = image.rgba8888.duplicate().apply {
            position(0)
            limit(image.width * image.height * ProcessedImage.BYTES_PER_PIXEL)
        }
        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.copyPixelsFromBuffer(pixels)
            contentResolver.openOutputStream(uri, "w")?.use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, stream)) {
                    throw IOException("Android JPEG encoder failed")
                }
            } ?: throw IOException("Unable to open MediaStore image for writing")
        } finally {
            bitmap.recycle()
        }
    }

    private fun writeExif(uri: Uri, metadata: CaptureMetadata, capturedAtMillis: Long) {
        contentResolver.openFileDescriptor(uri, "rw")?.use { descriptor ->
            val exif = ExifInterface(descriptor.fileDescriptor)
            val date = EXIF_DATE_FORMAT.get().format(Date(capturedAtMillis))
            exif.setAttribute(ExifInterface.TAG_DATETIME, date)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, date)
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, date)
            exif.setAttribute(ExifInterface.TAG_MAKE, android.os.Build.MANUFACTURER)
            exif.setAttribute(ExifInterface.TAG_MODEL, android.os.Build.MODEL)
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Unspektrawesome")
            exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, metadata.lensModel)
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            metadata.exposureTimeNs?.takeIf { it > 0L }?.let {
                exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, (it / 1_000_000_000.0).toString())
            }
            metadata.sensitivityIso?.takeIf { it > 0 }?.let {
                exif.setAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS, it.toString())
            }
            metadata.focalLengthMm?.takeIf { it.isFinite() && it > 0f }?.let {
                exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, "${(it * 1000f).toInt()}/1000")
            }
            exif.saveAttributes()
        } ?: throw IOException("Unable to open MediaStore image for EXIF writing")
    }

    companion object {
        const val DEFAULT_JPEG_QUALITY = 95
        private val PICTURES_DIRECTORY = Environment.DIRECTORY_DCIM
        private const val ALBUM_NAME = "Unspektrawesome"
        private val FILE_DATE_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat = SimpleDateFormat(
                "yyyyMMdd_HHmmss_SSS",
                Locale.US,
            )
        }
        private val EXIF_DATE_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat = SimpleDateFormat(
                "yyyy:MM:dd HH:mm:ss",
                Locale.US,
            ).apply { timeZone = TimeZone.getDefault() }
        }
    }
}

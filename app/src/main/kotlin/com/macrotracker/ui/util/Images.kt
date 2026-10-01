package com.macrotracker.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Photos for an AI to read (a nutrition label, a meal) are scaled so their long side is at
 * most this: plenty for small print, a few hundred kB as JPEG instead of several MB.
 */
const val AI_PHOTO_MAX_SIDE = 1600

/** A photo ready to show and to send: the scaled bitmap and its JPEG as base64. */
class PreparedPhoto(val bitmap: Bitmap, val base64: String)

/**
 * Reads the image at [uri] scaled down to [maxSide] and turned upright by its EXIF.
 * Never decodes the full resolution (a 50 MP photo would not fit in memory), and is meant
 * for a background thread. Null when the image can't be read.
 */
fun preparePhoto(context: Context, uri: Uri, maxSide: Int = AI_PHOTO_MAX_SIDE): PreparedPhoto? = runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide) }
    val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
    val rotation = resolver.openInputStream(uri)?.use { exifRotation(ExifInterface(it)) } ?: 0
    decoded.upright(rotation, maxSide).prepared()
}.getOrNull() // runCatching takes OutOfMemoryError too: a photo too big still only fails this photo.

/** The same for a camera capture's JPEG [bytes], turned by the [rotationDegrees] CameraX reports. */
fun preparePhoto(bytes: ByteArray, rotationDegrees: Int, maxSide: Int = AI_PHOTO_MAX_SIDE): PreparedPhoto? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide) }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
    decoded.upright(rotationDegrees, maxSide).prepared()
}.getOrNull()

/** The largest power of two that keeps the long side at or above [maxSide]; the exact fit comes after. */
internal fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
    var sample = 1
    while (max(width, height) / (sample * 2) >= maxSide) sample *= 2
    return sample
}

private fun exifRotation(exif: ExifInterface): Int = when (
    exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
) {
    ExifInterface.ORIENTATION_ROTATE_90 -> 90
    ExifInterface.ORIENTATION_ROTATE_180 -> 180
    ExifInterface.ORIENTATION_ROTATE_270 -> 270
    else -> 0
}

/** Scaled to fit [maxSide] and rotated in one pass. */
private fun Bitmap.upright(rotationDegrees: Int, maxSide: Int): Bitmap {
    val scale = (maxSide.toFloat() / max(width, height)).coerceAtMost(1f)
    if (scale == 1f && rotationDegrees % 360 == 0) return this
    val matrix = Matrix().apply {
        postScale(scale, scale)
        postRotate(rotationDegrees.toFloat())
    }
    val out = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    if (out !== this) recycle()
    return out
}

private fun Bitmap.prepared(): PreparedPhoto {
    val stream = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, 70, stream)
    return PreparedPhoto(this, Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP))
}

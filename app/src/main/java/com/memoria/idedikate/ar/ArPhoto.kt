package com.memoria.idedikate.ar

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/** Snapshots of the AR view (camera feed plus the memorial), ready to share to other apps. */
object ArPhoto {

    private const val SHARE_DIR = "shared"
    private const val JPEG_QUALITY = 92

    /**
     * Copies what the AR view is currently showing. The Compose overlays (mode chips, status text)
     * aren't part of the SurfaceView, so they're left out. Returns null if the view can't be read.
     */
    suspend fun capture(view: SurfaceView): Bitmap? {
        if (view.width == 0 || view.height == 0 || !view.holder.surface.isValid) return null
        val bitmap = createBitmap(view.width, view.height)
        val result = suspendCancellableCoroutine { continuation ->
            PixelCopy.request(
                view,
                bitmap,
                { result -> continuation.resume(result) },
                Handler(Looper.getMainLooper())
            )
        }
        if (result != PixelCopy.SUCCESS) {
            Log.w("ArPhoto", "PixelCopy failed with result $result")
            bitmap.recycle()
            return null
        }
        return bitmap
    }

    /** Adds a caption strip with the memorial's name along the bottom of the photo. */
    fun addCaption(photo: Bitmap, title: String) {
        val canvas = Canvas(photo)
        val unit = photo.width / 100f
        val stripHeight = unit * 16

        canvas.drawRect(
            0f, photo.height - stripHeight, photo.width.toFloat(), photo.height.toFloat(),
            Paint().apply { color = Color.argb(140, 0, 0, 0) }
        )

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = unit * 5
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val creditPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 255, 255, 255)
            textSize = unit * 3
            textAlign = Paint.Align.CENTER
        }
        val maxWidth = photo.width - unit * 8
        val line = TextUtils.ellipsize(title, TextPaint(titlePaint), maxWidth, TextUtils.TruncateAt.END)
        val centerX = photo.width / 2f
        canvas.drawText(line.toString(), centerX, photo.height - stripHeight + unit * 7.5f, titlePaint)
        canvas.drawText("iDedikate", centerX, photo.height - stripHeight + unit * 12.5f, creditPaint)
    }

    /** Saves the photo to the app cache and opens the system share sheet (social media, messaging, …). */
    suspend fun share(context: Context, photo: Bitmap, title: String) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
            // Only the latest photo is needed; the share target has read it by the next capture
            dir.listFiles()?.forEach { it.delete() }
            File(dir, "memorial_${System.currentTimeMillis()}.jpg").also { file ->
                file.outputStream().use { photo.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "$title, shared from iDedikate")
            // ClipData lets the chooser's preview and the target app read the image
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share memorial photo"))
    }
}

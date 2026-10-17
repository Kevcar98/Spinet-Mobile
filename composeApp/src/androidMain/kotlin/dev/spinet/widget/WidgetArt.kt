package dev.spinet.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

internal object WidgetArt {
    private const val MAX_EDGE = 192

    private const val BACKDROP_W = 300
    private const val BACKDROP_H = 150

    private const val BLUR_SOURCE_EDGE = 40

    suspend fun loadPair(context: Context, url: String): Pair<Bitmap?, Bitmap?> {
        if (url.isBlank()) return null to null
        return withContext(Dispatchers.IO) {
            runCatching {
                val bytes = fetchBytes(context, url) ?: return@runCatching null to null
                decodeScaled(bytes, MAX_EDGE) to runCatching { bannerFrom(bytes) }.getOrNull()
            }.getOrElse { null to null }
        }
    }

    private fun bannerFrom(bytes: ByteArray): Bitmap? {
        val decoded = decodeScaled(bytes, BLUR_SOURCE_EDGE * 2) ?: return null
        val source = shrinkToEdge(decoded, BLUR_SOURCE_EDGE)
        val banner = blurredBanner(source)
        if (source !== decoded) source.recycle()
        decoded.recycle()
        return banner
    }

    private fun blurredBanner(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(BACKDROP_W, BACKDROP_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val srcAspect = source.width.toFloat() / source.height
        val dstAspect = BACKDROP_W.toFloat() / BACKDROP_H
        val src = if (srcAspect > dstAspect) {
            val w = (source.height * dstAspect).toInt().coerceAtMost(source.width)
            Rect((source.width - w) / 2, 0, (source.width - w) / 2 + w, source.height)
        } else {
            val h = (source.width / dstAspect).toInt().coerceAtMost(source.height)
            Rect(0, (source.height - h) / 2, source.width, (source.height - h) / 2 + h)
        }
        canvas.drawBitmap(
            source,
            src,
            Rect(0, 0, BACKDROP_W, BACKDROP_H),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )

        val scrim = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, BACKDROP_H.toFloat(),
                Color.argb(115, 0, 0, 0),
                Color.argb(191, 0, 0, 0),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, BACKDROP_W.toFloat(), BACKDROP_H.toFloat(), scrim)
        return out
    }

    private fun shrinkToEdge(bitmap: Bitmap, edge: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= edge) return bitmap
        val scale = edge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
             true
        )
    }

    private fun fetchBytes(context: Context, url: String): ByteArray? =
        openStream(context, sizedForWidget(url))?.use { it.readBytes() }
            ?.takeIf { it.isNotEmpty() }

    private fun sizedForWidget(url: String): String {
        if (!url.startsWith("http", ignoreCase = true)) return url
        if (!url.contains("/art/")) return url
        if (url.contains("size=")) return url
        return url + (if (url.contains('?')) "&" else "?") + "size=" + MAX_EDGE
    }

    private const val RING_PX = 144

    fun progressRing(progress: Float, onArt: Boolean): Bitmap {
        val out = Bitmap.createBitmap(RING_PX, RING_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val stroke = RING_PX * 0.07f

        val bounds = RectF(
            stroke / 2f,
            stroke / 2f,
            RING_PX - stroke / 2f,
            RING_PX - stroke / 2f
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }

        paint.color = if (onArt) Color.argb(70, 255, 255, 255) else Color.argb(60, 128, 128, 128)
        canvas.drawArc(bounds, 0f, 360f, false, paint)

        paint.color = if (onArt) Color.WHITE else Color.argb(255, 120, 120, 120)

        canvas.drawArc(bounds, -90f, 360f * progress.coerceIn(0f, 1f), false, paint)
        return out
    }

    private fun openStream(context: Context, url: String): InputStream? =
        if (url.startsWith("http")) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 2_000
                readTimeout = 2_000
            }
            connection.inputStream
        } else {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(url))
            }.getOrNull()
        }

    private fun decodeScaled(bytes: ByteArray, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)

        val options = BitmapFactory.Options().apply {
            inSampleSize = if (longest > maxEdge) {
                var sample = 1
                while (longest / (sample * 2) >= maxEdge) sample *= 2
                sample
            } else 1
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}

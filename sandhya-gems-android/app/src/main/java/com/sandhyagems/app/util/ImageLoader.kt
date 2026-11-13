package com.sandhyagems.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import com.sandhyagems.app.R
import com.sandhyagems.app.data.Shopify
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * A small image loader: memory cache, disk cache, and downloads sized to the
 * view. Written by hand to keep the app tiny; it does what this app needs and
 * no more.
 */
object ImageLoader {
    private const val DISK_LIMIT_BYTES = 40L * 1024 * 1024

    private val memory = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(32L * 1024 * 1024).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private var diskDir: File? = null

    /** Tests set this to supply pictures without a network. */
    @Volatile
    var override: ((String) -> Bitmap?)? = null

    fun init(context: Context) {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        diskDir = dir
        AppExecutors.images.execute { trimDisk(dir) }
    }

    /** Shows [url] in [view], scaled for a view [widthPx] wide. A null url clears the view. */
    fun load(view: ImageView, url: String?, widthPx: Int) {
        val target = Shopify.sizedImage(url, widthPx)
        view.setTag(R.id.tag_image_url, target)
        view.animate().cancel()
        view.alpha = 1f
        if (target == null) {
            view.setImageDrawable(null)
            return
        }
        override?.let { supply ->
            view.setImageBitmap(supply(target))
            return
        }
        memory.get(target)?.let {
            view.setImageBitmap(it)
            return
        }
        view.setImageDrawable(null)
        AppExecutors.images.execute {
            val bitmap = try {
                fetch(target, widthPx)
            } catch (e: Exception) {
                null
            } ?: return@execute
            memory.put(target, bitmap)
            AppExecutors.main.post {
                // The view may have been recycled for another product meanwhile.
                if (view.getTag(R.id.tag_image_url) == target) {
                    view.setImageBitmap(bitmap)
                    view.alpha = 0f
                    view.animate().alpha(1f).setDuration(160).start()
                }
            }
        }
    }

    private fun fetch(url: String, widthPx: Int): Bitmap? {
        val dir = diskDir ?: return null
        val file = File(dir, sha1(url))
        if (file.exists()) {
            file.setLastModified(System.currentTimeMillis())
        } else {
            download(url, file)
        }
        return decode(file, widthPx)
    }

    private fun download(url: String, file: File) {
        val tmp = File(file.parentFile, file.name + ".tmp" + Thread.currentThread().id)
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            if (connection.responseCode !in 200..299) return
            connection.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(file)
        } finally {
            connection.disconnect()
            tmp.delete()
        }
    }

    private fun decode(file: File, widthPx: Int): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) {
            file.delete()
            return null
        }
        var sample = 1
        while (widthPx > 0 && bounds.outWidth / (sample * 2) >= widthPx) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Deletes the least recently used pictures once the disk cache passes its limit. */
    private fun trimDisk(dir: File) {
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= DISK_LIMIT_BYTES) break
            total -= file.length()
            file.delete()
        }
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}

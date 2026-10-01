package com.example.localthemeloader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

class ThemeException(message: String) : Exception(message)
data class ThemePackage(
    val id: String, val name: String, val author: String, val version: String, val versionCode: Int,
    val schemaVersion: Int, val description: String, val minAndroid: Int, val root: File,
    val previewFile: File?, val homePreview: File?, val lockPreview: File?,
    val widgetsPreview: File?, val iconsPreview: File?, val homeWallpaper: File, val lockWallpaper: File,
    val icons: Map<String, File>, val categories: Map<String, File>, val widgets: Map<String, WidgetConfig>, val accentColor: Int
) {
    val sourceZip: File get() = File(root, "source.ltheme")
    val bytes: Long get() = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
object ThemeFiles {
    fun validId(id: String) = id.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,79}"))
    fun safeFile(root: File, path: String): File {
        if (path.isBlank() || path.length > 240 || path.startsWith('/') || path.contains('\\') || path.contains(':') ||
            path.split('/').any { it == ".." || it == "." || it.isEmpty() }) throw ThemeException("主题包含不安全的文件路径。")
        val file = File(root, path)
        if (!file.canonicalPath.startsWith(root.canonicalPath + File.separator)) throw ThemeException("主题文件超出了允许的范围。")
        return file
    }
    fun decode(file: File?, maxEdge: Int = 1080): Bitmap? {
        if (file == null || !file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > 8192 || bounds.outHeight > 8192 ||
            bounds.outWidth.toLong() * bounds.outHeight > 24000000L) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge * 2) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }
    fun validateImage(file: File) {
        val bitmap = decode(file, 128) ?: throw ThemeException("主题中有缺失、损坏或尺寸过大的图片。")
        bitmap.recycle()
    }
}

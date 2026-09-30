package com.example.localthemeloader

import android.app.WallpaperManager
import android.content.Context
import android.graphics.*
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

data class ThemePackage(
    val id: String, val name: String, val author: String, val version: String,
    val description: String, val minAndroid: Int, val root: File,
    val sourceZip: File?, val previewFile: File?, val homePreview: File?,
    val lockPreview: File?, val iconsPreview: File?,
    val homeWallpaper: File?, val lockWallpaper: File?,
    val icons: Map<String, File>, val categories: Map<String, File>, val textColor: Int
) {
    val bytes: Long get() = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
class ThemeException(message: String) : Exception(message)
data class ImportResult(val theme: ThemePackage, val updated: Boolean)
data class ApplyResult(val messages: List<String>, val wallpaperCount: Int)

class ThemeManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("shiguang", Context.MODE_PRIVATE)
    private val themesDir = File(context.filesDir, "themes").apply { mkdirs() }
    private val workDir = File(context.filesDir, "theme_work").apply { mkdirs() }

    init {
      synchronized(guard) { if (!recovered) {
        // Keep the previous version until replacement is durable; recover an interrupted import.
        workDir.listFiles()?.filter { it.name.startsWith("backup-") }?.forEach { backup ->
            val target = File(themesDir, backup.name.removePrefix("backup-"))
            if (!target.exists()) backup.renameTo(target) else backup.deleteRecursively()
        }
        workDir.listFiles()?.filter { it.name.startsWith("incoming-") }?.forEach { it.deleteRecursively() }
        recovered = true
      } }
    }
    fun listThemes(): List<ThemePackage> = synchronized(guard) {
        themesDir.listFiles()?.filter { it.isDirectory }?.mapNotNull { readTheme(it) }
            ?.sortedByDescending { it.root.lastModified() } ?: emptyList()
    }
    fun currentTheme(): ThemePackage? = prefs.getString("active_theme", null)?.let { themeById(it) }
    fun themeById(id: String): ThemePackage? = if (validId(id)) readTheme(File(themesDir, id)) else null
    fun hasWelcome() = prefs.getBoolean("welcome", false)
    fun dismissWelcome() { prefs.edit().putBoolean("welcome", true).apply() }
    fun storageBytes(): Long = themesDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    fun importTheme(uri: Uri): ImportResult = context.contentResolver.openInputStream(uri)?.use { importStream(it) }
        ?: throw ThemeException("无法读取这个文件，请重新下载主题包后再试。")

    fun importStream(input: InputStream): ImportResult = synchronized(guard) {
        val staging = File(workDir, "incoming-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val archive = File(staging, "source.ltheme")
            var sourceBytes = 0L
            archive.outputStream().use { out ->
                val buffer = ByteArray(32768)
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    sourceBytes += n
                    if (sourceBytes > 150L * 1024 * 1024) throw ThemeException("主题包超过 150 MB，请使用较小的主题包。")
                    out.write(buffer, 0, n)
                }
            }
            var count = 0; var total = 0L
            val paths = mutableSetOf<String>()
            ZipInputStream(archive.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++count > 2000) throw ThemeException("主题包文件太多，无法导入。")
                    val path = entry.name
                    if (path.contains('\\') || path.startsWith('/') || path.contains(':') ||
                        path.split('/').any { it == ".." || it == "." } || path == "source.ltheme" ||
                        !paths.add(path) || path.length > 240) throw ThemeException("主题包包含不安全或重复的文件路径。")
                    val out = safeFile(staging, path) ?: throw ThemeException("主题包包含不安全的文件路径。")
                    if (entry.isDirectory) out.mkdirs() else {
                        out.parentFile?.mkdirs(); var fileBytes = 0L
                        out.outputStream().use { dest ->
                            val buffer = ByteArray(32768)
                            while (true) {
                                val n = zip.read(buffer); if (n < 0) break
                                total += n; fileBytes += n
                                if (total > 300L * 1024 * 1024 || fileBytes > 50L * 1024 * 1024)
                                    throw ThemeException("主题资源过大，请联系作者压缩图片后重新打包。")
                                dest.write(buffer, 0, n)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            val meta = File(staging, "manifest.json").takeIf { it.isFile }
                ?: File(staging, "theme.json").takeIf { it.isFile }
                ?: throw ThemeException("缺少主题信息，请选择完整的 .ltheme 主题包。")
            if (meta.length() > 128 * 1024) throw ThemeException("主题信息文件过大。")
            val json = try { JSONObject(meta.readText()) } catch (_: Exception) { throw ThemeException("主题信息无法读取，请联系作者重新打包。") }
            val name = json.optString("name").trim()
            if (name.isBlank() || name.length > 80) throw ThemeException("主题名称为空或过长。")
            val id = json.optString("id").ifBlank {
                if (meta.name == "manifest.json") throw ThemeException("缺少主题编号，请联系作者重新打包。")
                "legacy_" + MessageDigest.getInstance("SHA-256").digest((name + json.optString("author")).toByteArray()).take(10).joinToString("") { "%02x".format(it) }
            }
            if (!validId(id)) throw ThemeException("主题编号格式不正确。")
            if (json.optString("version", "1.0.0").length > 30 || json.optString("author").length > 80 || json.optString("description").length > 2000)
                throw ThemeException("主题信息过长。")
            if (json.optInt("schema_version", 1) != 1) throw ThemeException("这个主题需要更新版本的拾光主题，请先更新软件。")
            json.put("id", id)
            File(staging, "manifest.json").writeText(json.toString(2))
            val theme = readTheme(staging) ?: throw ThemeException("主题信息不完整。")
            val home = theme.homeWallpaper ?: throw ThemeException("缺少桌面壁纸。")
            val lock = theme.lockWallpaper ?: throw ThemeException("缺少锁屏壁纸。")
            if (theme.icons.isEmpty() && theme.categories.isEmpty()) throw ThemeException("缺少主题图标。")
            if (theme.minAndroid < 10 || theme.minAndroid > 99) throw ThemeException("主题的安卓版本信息不正确。")
            (listOf(home, lock) + theme.icons.values + theme.categories.values + listOfNotNull(theme.previewFile, theme.homePreview, theme.lockPreview, theme.iconsPreview))
                .distinct().forEach { validateImage(it) }
            createMissingPreviews(theme)
            val target = File(themesDir, id); val backup = File(workDir, "backup-$id")
            val updated = target.exists()
            if (backup.exists()) backup.deleteRecursively()
            if (updated && !target.renameTo(backup)) throw ThemeException("旧主题正在使用，请稍后再试。")
            if (!staging.renameTo(target)) {
                if (updated) backup.renameTo(target)
                throw ThemeException("空间不足，主题未保存。请清理存储后重试。")
            }
            target.setLastModified(System.currentTimeMillis()); backup.deleteRecursively()
            ImportResult(readTheme(target) ?: error("Durable theme missing"), updated)
        } catch (e: Exception) {
            staging.deleteRecursively()
            if (e is ThemeException) throw e
            throw ThemeException("主题包损坏或存储空间不足，请重新下载后再试。")
        }
    }
    fun deleteTheme(id: String): Boolean = synchronized(guard) {
        if (!validId(id) || currentTheme()?.id == id) return false
        File(themesDir, id).deleteRecursively()
    }
    private fun readTheme(root: File): ThemePackage? = try {
        val meta = File(root, "manifest.json").takeIf { it.isFile } ?: File(root, "theme.json")
        if (!meta.isFile || meta.length() > 128 * 1024) null else {
            val json = JSONObject(meta.readText())
            fun asset(path: String): File? = safeFile(root, path)?.takeIf { it.isFile }
            val walls = json.optJSONObject("wallpapers"); val previews = json.optJSONObject("preview")
            val home = asset(walls?.optString("home", "wallpapers/home.png") ?: json.optString("home_wallpaper", "wallpapers/home.png"))
            val lock = asset(walls?.optString("lock", "wallpapers/lock.png") ?: json.optString("lock_wallpaper", "wallpapers/lock.png"))
            val icons = linkedMapOf<String, File>()
            json.optJSONObject("icons")?.let { obj -> obj.keys().forEach { key -> asset(obj.optString(key))?.let { icons[key] = it } } }
            File(root, "icons").listFiles()?.filter { it.isFile && it.extension.lowercase() in setOf("png", "webp", "jpg", "jpeg") }?.forEach { icons.putIfAbsent(it.nameWithoutExtension, it) }
            val categories = linkedMapOf<String, File>()
            json.optJSONObject("icon_categories")?.let { obj -> obj.keys().forEach { key -> asset(obj.optString(key))?.let { categories[key] = it } } }
            val cover = listOfNotNull(asset(previews?.optString("cover", "preview/cover.png") ?: "preview/cover.png"), asset(json.optString("preview")), asset("preview.png"), asset("preview.jpg"), home).firstOrNull()
            val hp = asset(previews?.optString("home", "preview/home.png") ?: "preview/home.png") ?: asset("previews/home.png") ?: home
            val lp = asset(previews?.optString("lock", "preview/lock.png") ?: "preview/lock.png") ?: asset("previews/lock.png") ?: lock
            val color = try { Color.parseColor(json.optJSONObject("launcher")?.optString("text_color", "#304E40") ?: "#304E40") } catch (_: Exception) { Color.WHITE }
            ThemePackage(json.optString("id", root.name), json.optString("name", "未命名主题"), json.optString("author", "主题作者"), json.optString("version", "1.0.0"),
                json.optString("description", "让日常多一点喜欢。"), json.optInt("min_android", 10), root, File(root, "source.ltheme").takeIf { it.exists() }, cover,
                hp, lp, asset(previews?.optString("icons", "preview/icons.png") ?: "preview/icons.png"), home, lock, icons, categories, color)
        }
    } catch (_: Exception) { null }
    private fun createMissingPreviews(theme: ThemePackage) {
        val dir = File(theme.root, "preview").apply { mkdirs() }
        fun thumb(source: File?, name: String) {
            val output = File(dir, name)
            if (!output.exists() && source != null) decodeSampled(source, 720)?.let { bmp ->
                output.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }; bmp.recycle()
            }
        }
        thumb(theme.previewFile ?: theme.homeWallpaper, "cover.png")
        thumb(theme.homePreview, "home.png"); thumb(theme.lockPreview, "lock.png")
        if (theme.iconsPreview == null) {
            val bitmap = Bitmap.createBitmap(720, 480, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap); canvas.drawColor(Color.rgb(247, 247, 240))
            (theme.categories.values + theme.icons.values).distinct().take(12).forEachIndexed { i, file ->
                decodeSampled(file, 120)?.let { icon ->
                    val left = 40 + (i % 4) * 175; val top = 30 + (i / 4) * 150
                    canvas.drawBitmap(icon, null, Rect(left, top, left + 110, top + 110), Paint(Paint.ANTI_ALIAS_FLAG)); icon.recycle()
                }
            }
            File(dir, "icons.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
    private fun validateImage(file: File) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0 || options.outWidth > 8192 || options.outHeight > 8192 ||
            options.outWidth.toLong() * options.outHeight > 24000000L) throw ThemeException("主题中有损坏或尺寸过大的图片。")
    }
    fun applyTheme(theme: ThemePackage): ApplyResult = synchronized(guard) {
        val current = themeById(theme.id) ?: throw ThemeException("主题已被删除，请重新导入。")
        val messages = mutableListOf<String>(); val wm = WallpaperManager.getInstance(context); var changed = 0
        if (wm.isWallpaperSupported && wm.isSetWallpaperAllowed) {
            listOf(Triple(current.homeWallpaper, WallpaperManager.FLAG_SYSTEM, "桌面壁纸"), Triple(current.lockWallpaper, WallpaperManager.FLAG_LOCK, "锁屏壁纸")).forEach { (file, flag, label) ->
                try {
                    file?.inputStream()?.use { wm.setStream(it, null, false, flag) }
                    if (file != null) { changed++; messages += "${label}已应用" }
                } catch (_: Exception) { messages += "${label}暂未更换：系统限制了此操作，可在系统设置中手动设置。" }
            }
        } else messages += "系统暂不允许更换壁纸；主题图标仍可用于拾光桌面。"
        prefs.edit().putString("active_theme", current.id).commit()
        ApplyResult(messages, changed)
    }
    fun restoreDefault(): List<String> = synchronized(guard) {
        val result = mutableListOf<String>(); val wm = WallpaperManager.getInstance(context)
        listOf(WallpaperManager.FLAG_LOCK to "锁屏", WallpaperManager.FLAG_SYSTEM to "桌面").forEach { (flag, label) ->
            try { wm.clear(flag); result += "${label}壁纸已恢复系统默认" }
            catch (_: Exception) { result += "${label}壁纸需在系统设置中恢复" }
        }
        prefs.edit().remove("active_theme").commit(); result += "拾光桌面已恢复原始应用图标"
        result
    }
    companion object {
        private val guard = Any()
        private var recovered = false
        fun validId(id: String) = id.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,79}"))
        fun safeFile(root: File, relative: String): File? {
            if (relative.isBlank() || relative.contains('\\') || relative.contains(':')) return null
            val file = File(root, relative)
            return file.takeIf { it.canonicalPath.startsWith(root.canonicalPath + File.separator) }
        }
        fun decodeSampled(file: File, maxEdge: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge * 2) sample *= 2
            return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}

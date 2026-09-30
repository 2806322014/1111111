package com.example.localthemeloader

import android.app.WallpaperManager
import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.zip.ZipInputStream

data class ThemePackage(
    val id: String,
    val name: String,
    val author: String,
    val root: File,
    val sourceZip: File?,
    val previewFile: File?,
    val homeWallpaper: File?,
    val lockWallpaper: File?,
    val icons: Map<String, File>
)

class ThemeManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("theme_loader", Context.MODE_PRIVATE)
    private val themesDir = File(context.filesDir, "themes").apply { mkdirs() }

    fun listThemes(): List<ThemePackage> =
        themesDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { readTheme(it) }
            ?.sortedByDescending { it.root.lastModified() }
            ?: emptyList()

    fun currentTheme(): ThemePackage? {
        val id = prefs.getString("current_theme", null) ?: return null
        return themeById(id)
    }

    fun themeById(id: String): ThemePackage? {
        val root = File(themesDir, id)
        if (!root.exists() || !root.isDirectory) return null
        return readTheme(root)
    }

    fun selectTheme(id: String): ThemePackage? {
        val theme = themeById(id) ?: return null
        prefs.edit().putString("current_theme", id).apply()
        return theme
    }

    fun deleteTheme(id: String): Boolean {
        val root = File(themesDir, id)
        if (!root.exists()) return false
        val deleted = root.deleteRecursively()
        if (deleted && prefs.getString("current_theme", null) == id) {
            val fallback = listThemes().firstOrNull()
            prefs.edit().putString("current_theme", fallback?.id).apply()
        }
        return deleted
    }

    fun importTheme(uri: Uri): ThemePackage {
        val id = UUID.randomUUID().toString()
        val target = File(themesDir, id).apply { mkdirs() }
        val savedZip = File(target, "package.zip")

        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取主题包" }
                savedZip.outputStream().use { output -> input.copyTo(output) }
            }

            FileInputStream(savedZip).use { input ->
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.name == "package.zip") {
                            zip.closeEntry()
                            continue
                        }
                        val outFile = File(target, entry.name)
                        val safeRoot = target.canonicalPath + File.separator
                        require(outFile.canonicalPath.startsWith(safeRoot)) { "主题包包含非法路径" }
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { output -> zip.copyTo(output) }
                        }
                        zip.closeEntry()
                    }
                }
            }

            val theme = readTheme(target) ?: error("主题包缺少 theme.json")
            target.setLastModified(System.currentTimeMillis())
            prefs.edit().putString("current_theme", id).apply()
            return theme
        } catch (e: Exception) {
            target.deleteRecursively()
            throw e
        }
    }

    private fun readTheme(root: File): ThemePackage? {
        val meta = File(root, "theme.json")
        if (!meta.exists()) return null

        val json = JSONObject(meta.readText())
        val name = json.optString("name", "未命名主题")
        val author = json.optString("author", "")
        val home = resolveSafe(root, json.optString("home_wallpaper", "wallpapers/home.png"))
        val lock = resolveSafe(root, json.optString("lock_wallpaper", "wallpapers/lock.png"))

        val explicitPreview = resolveSafe(root, json.optString("preview", ""))
        val preview = listOfNotNull(
            explicitPreview,
            File(root, "preview.png"),
            File(root, "preview.jpg"),
            File(root, "previews/home.png"),
            File(root, "previews/lock.png"),
            home,
            lock
        ).firstOrNull { it.exists() && it.isFile }

        val icons = mutableMapOf<String, File>()
        val obj = json.optJSONObject("icons")
        if (obj != null) {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val pkg = keys.next()
                val file = resolveSafe(root, obj.optString(pkg, ""))
                if (file?.exists() == true) icons[pkg] = file
            }
        }

        val iconsDir = File(root, "icons")
        iconsDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp") }
            ?.forEach { f -> icons.putIfAbsent(f.nameWithoutExtension, f) }

        return ThemePackage(
            id = root.name,
            name = name,
            author = author,
            root = root,
            sourceZip = File(root, "package.zip").takeIf { it.exists() },
            previewFile = preview,
            homeWallpaper = home?.takeIf { it.exists() },
            lockWallpaper = lock?.takeIf { it.exists() },
            icons = icons
        )
    }

    private fun resolveSafe(root: File, relative: String): File? {
        if (relative.isBlank()) return null
        val f = File(root, relative)
        val safeRoot = root.canonicalPath + File.separator
        return f.takeIf { it.canonicalPath.startsWith(safeRoot) }
    }

    fun applyWallpapers(theme: ThemePackage): List<String> {
        val result = mutableListOf<String>()
        val wm = WallpaperManager.getInstance(context)
        if (!wm.isWallpaperSupported || !wm.isSetWallpaperAllowed) {
            return listOf("系统不允许本应用修改壁纸")
        }

        theme.homeWallpaper?.let { file ->
            FileInputStream(file).use { input ->
                wm.setStream(input, null, false, WallpaperManager.FLAG_SYSTEM)
            }
            result += "桌面壁纸已应用"
        }

        theme.lockWallpaper?.let { file ->
            FileInputStream(file).use { input ->
                wm.setStream(input, null, false, WallpaperManager.FLAG_LOCK)
            }
            result += "锁屏壁纸已应用"
        }

        if (result.isEmpty()) result += "主题包里没有可应用的壁纸"
        return result
    }
}

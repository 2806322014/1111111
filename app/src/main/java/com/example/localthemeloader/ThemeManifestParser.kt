package com.example.localthemeloader

import android.graphics.Color
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

object ThemeManifestParser {
    fun parse(root: File, validate: Boolean = false): ThemePackage {
        try {
            val meta = File(root, "manifest.json").takeIf { it.isFile } ?: File(root, "theme.json")
            if (!meta.isFile || meta.length() > 128 * 1024) throw ThemeException("缺少主题信息，或信息文件过大。")
            val j = JSONObject(meta.readText(Charsets.UTF_8))
            val rawSchema = j.opt("schema_version")
            if (meta.name == "manifest.json" && rawSchema != null && (rawSchema !is Number || rawSchema.toDouble() != rawSchema.toInt().toDouble())) throw ThemeException("主题格式版本必须是整数。")
            val schema = if (meta.name == "theme.json") 1 else j.optInt("schema_version", 1)
            if (schema !in 1..2) throw ThemeException("主题格式暂不支持，请检查主题包或更新拾光主题。")
            val name = j.optString("name").trim()
            if (name.isBlank() || name.length > 80) throw ThemeException("主题名称为空或过长。")
            val id = j.optString("id").ifBlank {
                if (meta.name != "theme.json") throw ThemeException("缺少主题编号。")
                "legacy_" + MessageDigest.getInstance("SHA-256").digest((name + j.optString("author")).toByteArray()).take(10).joinToString("") { "%02x".format(it) }
            }
            if (!ThemeFiles.validId(id)) throw ThemeException("主题编号格式不正确。")
            val version = j.optString("version", "1.0.0")
            val code = if (schema == 2) {
                val value = j.opt("version_code")
                if (value !is Number || value.toDouble() != value.toInt().toDouble() || value.toInt() <= 0) throw ThemeException("主题版本号必须是大于零的整数。")
                value.toInt()
            } else j.optInt("version_code", 1).coerceAtLeast(1)
            val author = j.optString("author", "主题作者"); val description = j.optString("description", "让日常多一点喜欢。")
            if (version.isBlank() || version.length > 30 || author.length > 80 || description.length > 2000) throw ThemeException("主题信息为空或过长。")
            val minAndroid = j.optInt("min_android", 10)
            if (minAndroid !in 10..99) throw ThemeException("主题的安卓版本信息不正确。")
            fun asset(path: String, required: Boolean = false): File? {
                if (path.isBlank()) { if (required) throw ThemeException("主题缺少必要的资源路径。"); return null }
                val f = ThemeFiles.safeFile(root, path)
                if (!f.isFile) { if (required) throw ThemeException("主题缺少壁纸或组件资源。"); return null }
                if (validate) ThemeFiles.validateImage(f)
                return f
            }
            val walls = j.optJSONObject("wallpapers"); val previews = j.optJSONObject("preview")
            val home = asset(walls?.optString("home", "wallpapers/home.png") ?: j.optString("home_wallpaper", "wallpapers/home.png"), true)!!
            val lock = asset(walls?.optString("lock", "wallpapers/lock.png") ?: j.optString("lock_wallpaper", "wallpapers/lock.png"), true)!!
            val icons = linkedMapOf<String, File>(); val categories = linkedMapOf<String, File>()
            j.optJSONObject("icons")?.let { obj -> obj.keys().forEach { key -> asset(obj.getString(key), true)?.let { icons[key] = it } } }
            j.optJSONObject("icon_categories")?.let { obj -> obj.keys().forEach { key -> asset(obj.getString(key), true)?.let { categories[key] = it } } }
            val iconsDir = ThemeFiles.safeFile(root, j.optString("icons_dir", "icons"))
            iconsDir.listFiles()?.filter { it.isFile && it.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp") }?.forEach { f ->
                if (validate) ThemeFiles.validateImage(f); icons.putIfAbsent(f.nameWithoutExtension, f)
            }
            val widgets = linkedMapOf<String, WidgetConfig>()
            j.optJSONObject("widgets")?.let { obj -> WidgetConfigParser.kinds.keys.forEach { kind ->
                if (obj.has(kind)) widgets[kind] = WidgetConfigParser.parse(root, ThemeFiles.safeFile(root, obj.getString(kind)), kind)
            } }
            fun preview(key: String): File? = asset(previews?.optString(key, "preview/$key.png") ?: "preview/$key.png")
            val cover = preview("cover") ?: (if (j.opt("preview") is String) asset(j.getString("preview")) else null) ?: asset("preview.png") ?: asset("preview.jpg") ?: home
            val hp = preview("home") ?: asset("previews/home.png") ?: home
            val lp = preview("lock") ?: asset("previews/lock.png") ?: lock
            return ThemePackage(id, name, author, version, code, schema, description, minAndroid, root, cover, hp, lp,
                preview("widgets"), preview("icons"), home, lock, icons, categories, widgets, Color.parseColor(j.optString("accent_color", "#315741")))
        } catch (e: ThemeException) { throw e }
        catch (_: Exception) { throw ThemeException("主题信息无法读取，请联系作者重新打包。") }
    }
}

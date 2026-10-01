package com.example.localthemeloader

import android.net.Uri
import android.graphics.*
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

class PreparedImport(val theme: ThemePackage, val previous: ThemePackage?) : AutoCloseable {
    override fun close() { if (theme.root.name.startsWith("incoming-")) theme.root.deleteRecursively() }
}
class ThemeImporter(private val repository: ThemeRepository) {
    fun prepare(uri: Uri): PreparedImport = repository.context.contentResolver.openInputStream(uri)?.use { prepare(it) }
        ?: throw ThemeException("无法读取这个主题文件，请重新选择。")
    fun prepare(input: InputStream): PreparedImport = synchronized(ThemeRepository.guard) {
        val staging = File(repository.workDir, "incoming-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val archive = File(staging, "source.ltheme"); var compressed = 0L
            archive.outputStream().use { out ->
                val buffer = ByteArray(32768)
                while (true) { val n = input.read(buffer); if (n < 0) break; compressed += n
                    if (compressed > 150L * 1024 * 1024) throw ThemeException("主题包超过 150 MB，请选择较小的文件。")
                    out.write(buffer, 0, n)
                }
            }
            var count = 0; var total = 0L; val paths = mutableSetOf<String>()
            ZipInputStream(archive.inputStream()).use { zip -> while (true) {
                val entry = zip.nextEntry ?: break
                if (++count > 2000) throw ThemeException("主题包文件太多，无法导入。")
                val path = entry.name.removeSuffix("/")
                if (path == "source.ltheme" || !paths.add(path)) throw ThemeException("主题包含保留或重复的文件路径。")
                val out = ThemeFiles.safeFile(staging, path)
                if (out.extension.lowercase() in setOf("apk", "dex", "so", "sh", "bat", "cmd", "ps1", "js", "py", "exe")) throw ThemeException("主题包只能包含图片和配置，不能包含程序或脚本。")
                if (entry.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs(); var fileBytes = 0L
                    out.outputStream().use { dest -> val buffer = ByteArray(32768)
                        while (true) { val n = zip.read(buffer); if (n < 0) break; total += n; fileBytes += n
                            if (total > 300L * 1024 * 1024 || fileBytes > 50L * 1024 * 1024) throw ThemeException("主题资源过大，请联系作者压缩后重新打包。")
                            dest.write(buffer, 0, n)
                        }
                    }
                }
                zip.closeEntry()
            } }
            var theme = ThemeManifestParser.parse(staging, true)
            if (!File(staging, "manifest.json").exists()) {
                val j = JSONObject(File(staging, "theme.json").readText()).put("id", theme.id)
                File(staging, "theme.json").writeText(j.toString(2))
            }
            createPreviews(theme)
            theme = ThemeManifestParser.parse(staging)
            PreparedImport(theme, repository.byId(theme.id))
        } catch (e: Exception) {
            staging.deleteRecursively()
            if (e is ThemeException) throw e
            throw ThemeException("主题包损坏或存储空间不足，请重新下载后再试。")
        }
    }
    fun commit(prepared: PreparedImport): ThemePackage = synchronized(ThemeRepository.guard) {
        val incoming = prepared.theme; val old = repository.byId(incoming.id)
        if (old != null && incoming.versionCode <= old.versionCode) throw ThemeException("已收藏相同或更新版本，无需重复导入。")
        if (old?.versionCode != prepared.previous?.versionCode) throw ThemeException("主题版本已发生变化，请重新导入确认。")
        if (!incoming.root.isDirectory || incoming.root.parentFile?.canonicalFile != repository.workDir.canonicalFile) throw ThemeException("待导入主题已失效，请重新选择。")
        val target = File(repository.themesDir, incoming.id); val backup = File(repository.workDir, "backup-${incoming.id}")
        if (backup.exists() && !backup.deleteRecursively()) throw ThemeException("暂时无法更新主题，请稍后再试。")
        if (target.exists() && !target.renameTo(backup)) throw ThemeException("旧主题正在使用，请稍后再试。")
        if (!incoming.root.renameTo(target)) {
            backup.renameTo(target); throw ThemeException("空间不足，旧版本已保留，请清理后重试。")
        }
        target.setLastModified(System.currentTimeMillis()); backup.deleteRecursively()
        repository.byId(incoming.id) ?: throw ThemeException("主题保存后无法读取，请重新导入。")
    }
    private fun createPreviews(theme: ThemePackage) {
        val dir = File(theme.root, "preview").apply { mkdirs() }
        fun thumb(file: File?, name: String) {
            val out = File(dir, name)
            if (!out.exists()) ThemeFiles.decode(file, 720)?.let { bmp -> out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }; bmp.recycle() }
        }
        thumb(theme.previewFile, "cover.png"); thumb(theme.homePreview, "home.png"); thumb(theme.lockPreview, "lock.png")
        if (theme.iconsPreview == null && (theme.icons.isNotEmpty() || theme.categories.isNotEmpty())) {
            val b = Bitmap.createBitmap(720, 480, Bitmap.Config.ARGB_8888); val c = Canvas(b); c.drawColor(Ui.bg)
            (theme.icons.values + theme.categories.values).distinct().take(12).forEachIndexed { i, f -> ThemeFiles.decode(f, 120)?.let { icon ->
                val x = 40 + (i % 4) * 175; val y = 30 + (i / 4) * 150
                c.drawBitmap(icon, null, Rect(x, y, x + 110, y + 110), Paint(Paint.ANTI_ALIAS_FLAG)); icon.recycle()
            } }
            File(dir, "icons.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle()
        }
        if (theme.widgetsPreview == null && theme.widgets.isNotEmpty()) {
            val b = Bitmap.createBitmap(720, 480, Bitmap.Config.ARGB_8888); val c = Canvas(b); c.drawColor(Ui.bg)
            theme.widgets.values.forEachIndexed { i, config ->
                val rect = RectF((i % 2) * 350f + 15, (i / 2) * 230f + 15, (i % 2) * 350f + 345, (i / 2) * 230f + 220)
                WidgetRenderer.drawCard(c, rect, config)
            }
            File(dir, "widgets.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle()
        }
    }
}

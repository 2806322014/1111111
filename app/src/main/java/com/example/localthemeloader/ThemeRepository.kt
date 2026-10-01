package com.example.localthemeloader

import android.content.Context
import java.io.File

class ThemeRepository(val context: Context) {
    val themesDir = File(context.filesDir, "themes").apply { mkdirs() }
    val workDir = File(context.filesDir, "theme_work").apply { mkdirs() }
    init { synchronized(guard) {
        if (recovered.add(context.filesDir.absolutePath)) {
            workDir.listFiles()?.filter { it.name.startsWith("backup-") }?.forEach { backup ->
                val id = backup.name.removePrefix("backup-")
                if (ThemeFiles.validId(id)) {
                    val target = File(themesDir, id)
                    if (!target.exists()) backup.renameTo(target) else backup.deleteRecursively()
                }
            }
            workDir.listFiles()?.filter { it.name.startsWith("incoming-") }?.forEach { it.deleteRecursively() }
        }
    } }
    fun byId(id: String): ThemePackage? = synchronized(guard) {
        if (!ThemeFiles.validId(id)) null else try { ThemeManifestParser.parse(File(themesDir, id)) } catch (_: Exception) { null }
    }
    fun list(): List<ThemePackage> = synchronized(guard) {
        themesDir.listFiles()?.filter { it.isDirectory }?.mapNotNull { byId(it.name) }?.sortedByDescending { it.root.lastModified() } ?: emptyList()
    }
    fun bytes(): Long = synchronized(guard) { themesDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
    fun delete(id: String): Boolean = synchronized(guard) {
        if (!ThemeFiles.validId(id)) return false
        val root = File(themesDir, id)
        if (root.exists() && !root.deleteRecursively()) return false
        ThemeApplyStateRepository(context).remove(id)
        WidgetConfigurationRepository(context).removeTheme(id)
        context.getSharedPreferences("shiguang", 0).edit().apply {
            if (context.getSharedPreferences("shiguang", 0).getString("active_theme", null) == id) remove("active_theme")
        }.commit()
        WidgetRenderer.refreshAll(context)
        true
    }
    companion object { val guard = Any(); private val recovered = mutableSetOf<String>() }
}

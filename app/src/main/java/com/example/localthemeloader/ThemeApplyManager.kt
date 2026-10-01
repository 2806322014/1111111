package com.example.localthemeloader

import android.app.WallpaperManager
import android.content.Context
import java.io.File

data class ApplySelection(val home: Boolean = true, val lock: Boolean = true, val widgets: Boolean = true, val icons: Boolean = false)
data class ApplyResult(val messages: List<String>, val state: ThemeApplyState)
interface WallpaperBackend { fun set(file: File, flag: Int) }
class SystemWallpaperBackend(context: Context) : WallpaperBackend {
    private val manager = WallpaperManager.getInstance(context)
    override fun set(file: File, flag: Int) {
        if (!manager.isWallpaperSupported || !manager.isSetWallpaperAllowed) throw UnsupportedOperationException()
        file.inputStream().use { manager.setStream(it, null, false, flag) }
    }
}
class ThemeApplyManager(private val context: Context, private val backend: WallpaperBackend = SystemWallpaperBackend(context)) {
    fun apply(theme: ThemePackage, selection: ApplySelection): ApplyResult = synchronized(ThemeRepository.guard) {
        val current = ThemeRepository(context).byId(theme.id) ?: throw ThemeException("主题已被删除，请重新导入。")
        if (current.versionCode != theme.versionCode) throw ThemeException("主题已更新，请重新打开详情后应用。")
        if (!CompatibilityManager(context).detect().accepts(current)) throw ThemeException("此主题需要 Android ${current.minAndroid} 或更高版本。")
        if (!selection.home && !selection.lock && !selection.widgets && !selection.icons) throw ThemeException("请至少选择一项要应用的内容。")
        val messages = mutableListOf<String>()
        fun wallpaper(selected: Boolean, file: File, flag: Int, label: String): Boolean {
            if (!selected) return false
            return try { backend.set(file, flag); messages += "✓ ${label}已应用"; true }
            catch (_: Exception) { messages += "△ ${label}：当前手机暂不支持自动设置该项目"; false }
        }
        val home = wallpaper(selection.home, current.homeWallpaper, WallpaperManager.FLAG_SYSTEM, "桌面壁纸")
        val lock = wallpaper(selection.lock, current.lockWallpaper, WallpaperManager.FLAG_LOCK, "锁屏壁纸")
        var widgets = false
        if (selection.widgets) {
            if (current.widgets.isEmpty()) messages += "△ 该主题暂无组件"
            else { WidgetConfigurationRepository(context).configureTheme(current); widgets = true; messages += "✓ 主题组件已配置，可在下一步添加" }
        }
        if (selection.icons) messages += "主题图标请在下一步选择应用并确认添加。"
        val state = ThemeApplyState(current.id, current.version, current.versionCode, home, lock, widgets, false, System.currentTimeMillis(),
            selection.home, selection.lock, selection.widgets && current.widgets.isNotEmpty(), selection.icons)
        ThemeApplyStateRepository(context).save(state)
        ApplyResult(messages, state)
    }
}

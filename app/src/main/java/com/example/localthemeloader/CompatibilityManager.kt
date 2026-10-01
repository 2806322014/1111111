package com.example.localthemeloader

import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.pm.ShortcutManager
import android.os.Build

data class Compatibility(val androidVersion: Int, val wallpaper: Boolean, val widgetPin: Boolean, val shortcutPin: Boolean) {
    fun accepts(theme: ThemePackage) = androidVersion >= theme.minAndroid
    fun description(theme: ThemePackage): String = listOf(
        if (accepts(theme)) "✓ 符合 Android ${theme.minAndroid}+ 要求" else "△ 此主题需要 Android ${theme.minAndroid} 或更高版本",
        if (wallpaper) "✓ 桌面壁纸可自动设置" else "△ 桌面壁纸需在手机设置中选择",
        if (wallpaper) "✓ 可尝试自动设置锁屏壁纸" else "△ 锁屏壁纸需在手机设置中选择",
        if (widgetPin) "✓ 可请求添加桌面组件" else "△ 当前桌面需要手动添加组件",
        if (shortcutPin) "✓ 可请求添加主题图标快捷方式" else "△ 当前桌面不支持添加主题图标快捷方式"
    ).joinToString("\n")
}
class CompatibilityManager(private val context: Context) {
    fun detect(): Compatibility = Compatibility(Build.VERSION.RELEASE.substringBefore('.').toIntOrNull() ?: (Build.VERSION.SDK_INT - 19),
        try { WallpaperManager.getInstance(context).let { it.isWallpaperSupported && it.isSetWallpaperAllowed } } catch (_: Exception) { false },
        try { AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported } catch (_: Exception) { false },
        try { context.getSystemService(ShortcutManager::class.java)?.isRequestPinShortcutSupported == true } catch (_: Exception) { false })
}

package com.example.localthemeloader

import android.content.Context
import org.json.JSONObject

data class ThemeApplyState(val themeId: String, val themeVersion: String, val themeVersionCode: Int, val homeWallpaperApplied: Boolean,
    val lockWallpaperApplied: Boolean, val widgetsConfigured: Boolean, val iconShortcutsRequested: Boolean,
    val appliedAt: Long, val homeSelected: Boolean, val lockSelected: Boolean, val widgetsSelected: Boolean, val iconsSelected: Boolean) {
    val hasAny: Boolean get() = homeWallpaperApplied || lockWallpaperApplied || widgetsConfigured || iconShortcutsRequested
    val label: String get() = if (!hasAny) "未应用" else if ((homeSelected && !homeWallpaperApplied) ||
        (lockSelected && !lockWallpaperApplied) || (widgetsSelected && !widgetsConfigured) || (iconsSelected && !iconShortcutsRequested)) "部分应用" else "已应用"
}
class ThemeApplyStateRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("theme_apply_v2", Context.MODE_PRIVATE)
    init {
        // V1 recorded only the active theme, without independent success results. Do not invent them during migration.
        context.getSharedPreferences("shiguang", 0).edit().remove("active_theme").apply()
    }
    fun get(id: String): ThemeApplyState? = try {
        (prefs.getString("state_$id", null) ?: prefs.getString(id, null))?.let { raw -> val j = JSONObject(raw)
            if (j.getString("themeId") != id) return@let null
            ThemeApplyState(j.getString("themeId"), j.getString("themeVersion"), j.optInt("themeVersionCode", 1), j.getBoolean("homeWallpaperApplied"),
                j.getBoolean("lockWallpaperApplied"), j.getBoolean("widgetsConfigured"), j.getBoolean("iconShortcutsRequested"),
                j.getLong("appliedAt"), j.getBoolean("homeSelected"), j.getBoolean("lockSelected"), j.getBoolean("widgetsSelected"), j.getBoolean("iconsSelected"))
        }
    } catch (_: Exception) { null }
    fun save(s: ThemeApplyState) {
        val j = JSONObject().put("themeId", s.themeId).put("themeVersion", s.themeVersion).put("themeVersionCode", s.themeVersionCode).put("homeWallpaperApplied", s.homeWallpaperApplied)
            .put("lockWallpaperApplied", s.lockWallpaperApplied).put("widgetsConfigured", s.widgetsConfigured).put("iconShortcutsRequested", s.iconShortcutsRequested)
            .put("appliedAt", s.appliedAt).put("homeSelected", s.homeSelected).put("lockSelected", s.lockSelected).put("widgetsSelected", s.widgetsSelected).put("iconsSelected", s.iconsSelected)
        prefs.edit().putString("state_${s.themeId}", j.toString()).putString("current", s.themeId).commit()
    }
    fun markWidgets(theme: ThemePackage) {
        val s = get(theme.id)?.takeIf { it.themeVersionCode == theme.versionCode } ?: empty(theme)
        save(s.copy(widgetsConfigured = true, widgetsSelected = true))
    }
    fun markShortcut(theme: ThemePackage) {
        val s = get(theme.id)?.takeIf { it.themeVersionCode == theme.versionCode } ?: empty(theme)
        save(s.copy(iconShortcutsRequested = true, iconsSelected = true))
    }
    private fun empty(t: ThemePackage) = ThemeApplyState(t.id, t.version, t.versionCode, false, false, false, false, System.currentTimeMillis(), false, false, false, false)
    fun remove(id: String) {
        val legacyBelongsToTheme = try { JSONObject(prefs.getString(id, "") ?: "").optString("themeId") == id } catch (_: Exception) { false }
        prefs.edit().remove("state_$id").apply {
            if (legacyBelongsToTheme) remove(id)
            if (prefs.getString("current", null) == id) remove("current")
        }.commit()
    }
    fun clear() {
        prefs.edit().clear().commit()
        context.getSharedPreferences("shiguang", 0).edit().remove("active_theme").commit()
        WidgetConfigurationRepository(context).clear()
        context.getSharedPreferences("shortcut_callbacks", 0).edit().clear().commit()
        WidgetRenderer.refreshAll(context)
    }
}

package com.example.localthemeloader

import android.appwidget.AppWidgetManager
import android.content.Context

class WidgetConfigurationRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("widgets_v2", 0)
    fun configureTheme(theme: ThemePackage) {
        val e = prefs.edit()
        WidgetConfigParser.kinds.keys.forEach { kind ->
            if (kind in theme.widgets) e.putString("preferred_$kind", theme.id) else e.remove("preferred_$kind")
        }
        WidgetRenderer.providers.forEach { provider ->
            val kind = WidgetRenderer.kind(provider.simpleName)
            AppWidgetManager.getInstance(context).getAppWidgetIds(android.content.ComponentName(context, provider)).forEach { id ->
                if (kind in theme.widgets) e.putString("widget_$id", theme.id) else e.remove("widget_$id")
            }
        }
        e.commit(); WidgetRenderer.refreshAll(context)
    }
    fun assign(id: Int, theme: ThemePackage) { prefs.edit().putString("widget_$id", theme.id).commit() }
    fun theme(id: Int) = prefs.getString("widget_$id", null)?.let { ThemeRepository(context).byId(it) }
    fun preferred(kind: String) = prefs.getString("preferred_$kind", null)
    fun remove(id: Int) { prefs.edit().remove("widget_$id").commit() }
    fun removeTheme(id: String) {
        val e = prefs.edit(); prefs.all.filterValues { it == id }.keys.forEach { e.remove(it) }; e.commit()
    }
    fun clear() { prefs.edit().clear().commit() }
}

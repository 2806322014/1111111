package com.example.localthemeloader

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.*
import android.net.Uri
import android.os.Bundle
import java.util.UUID

interface WidgetPinBackend { fun supported(): Boolean; fun request(component: ComponentName, callback: PendingIntent, preview: Bundle): Boolean }
class SystemWidgetPinBackend(private val context: Context) : WidgetPinBackend {
    override fun supported() = AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported
    override fun request(component: ComponentName, callback: PendingIntent, preview: Bundle) = AppWidgetManager.getInstance(context).requestPinAppWidget(component, preview, callback)
}
class WidgetPinManager(private val context: Context, private val backend: WidgetPinBackend = SystemWidgetPinBackend(context)) {
    fun request(theme: ThemePackage, kind: String, wide: Boolean): String {
        if (kind !in theme.widgets) return "该主题暂无${WidgetConfigParser.kinds[kind] ?: ""}组件"
        return try {
            if (!backend.supported()) return MANUAL_MESSAGE
            val intent = Intent(context, WidgetPinnedReceiver::class.java).setAction("com.example.localthemeloader.WIDGET_PINNED")
                .setData(Uri.parse("shiguang://widget/${UUID.randomUUID()}"))
                .putExtra("theme_id", theme.id).putExtra("kind", kind).putExtra("wide", wide)
            // The launcher supplies EXTRA_APPWIDGET_ID; the intent is explicit and targets a non-exported receiver.
            val callback = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            val component = WidgetRenderer.component(context, kind, wide)
            val preview = Bundle().apply { putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, WidgetRenderer.views(context, -1, component, theme)) }
            if (backend.request(component, callback, preview)) "请在桌面的确认窗口中添加组件。" else MANUAL_MESSAGE
        } catch (_: Exception) { MANUAL_MESSAGE }
    }
    companion object { const val MANUAL_MESSAGE = "当前桌面不支持自动添加，请长按桌面 → 小组件 → 拾光主题 手动添加。" }
}
class WidgetPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val theme = intent.getStringExtra("theme_id")?.let { ThemeRepository(context).byId(it) } ?: return
        val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(id) ?: return
        val kind = WidgetRenderer.kind(info.provider.className)
        if (info.provider.packageName != context.packageName || kind !in theme.widgets) return
        WidgetConfigurationRepository(context).assign(id, theme); WidgetRenderer.refresh(context, id)
        ThemeApplyStateRepository(context).markWidgets(theme)
    }
}

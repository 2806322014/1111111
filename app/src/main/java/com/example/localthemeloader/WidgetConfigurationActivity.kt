package com.example.localthemeloader

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle

class WidgetConfigurationActivity : ThemeScreenActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); setResult(RESULT_CANCELED)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id) ?: run { finish(); return }
        if (info.provider.packageName != packageName) { finish(); return }
        val kind = WidgetRenderer.kind(info.provider.className); val content = frame("选择${WidgetConfigParser.kinds[kind] ?: "桌面"}组件主题")
        Ui.add(content, Ui.text(this, "选择喜欢的搭配", 27f, Ui.ink, true)); Ui.add(content, Ui.space(this, 20))
        val themes = repository.list().filter { kind in it.widgets }
        if (themes.isEmpty()) { Ui.add(content, Ui.text(this, "尚未收藏带${WidgetConfigParser.kinds[kind]}组件的主题。可以使用拾光默认样式，或返回导入主题。", 16f, Ui.muted)); Ui.add(content, Ui.space(this, 20)) }
        themes.forEach { theme -> Ui.add(content, Ui.button(this, theme.name) { WidgetConfigurationRepository(this).assign(id, theme); WidgetRenderer.refresh(this, id); ThemeApplyStateRepository(this).markWidgets(theme)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)); finish() }); Ui.add(content, Ui.space(this, 12)) }
        Ui.add(content, Ui.button(this, "使用默认样式") { WidgetConfigurationRepository(this).remove(id); WidgetRenderer.refresh(this, id)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)); finish() })
    }
}

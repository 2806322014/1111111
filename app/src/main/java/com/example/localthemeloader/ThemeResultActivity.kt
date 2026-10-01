package com.example.localthemeloader

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle

class ThemeResultActivity : ThemeScreenActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val theme = intent.getStringExtra("theme_id")?.let { repository.byId(it) } ?: run { finish(); return }
        val applied = ThemeApplyStateRepository(this).get(theme.id)
        val title = if (applied?.label == "已应用") "《${theme.name}》应用完成" else if (applied?.hasAny == true) "《${theme.name}》部分应用完成" else "《${theme.name}》已准备"
        val content = frame("应用结果", footer = "返回主题库" to { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish() })
        Ui.add(content, Ui.text(this, title, 28f, Ui.ink, true)); Ui.add(content, Ui.space(this, 20)); Ui.add(content, Ui.text(this, intent.getStringArrayListExtra("messages")?.joinToString("\n\n") ?: "执行记录已保存。", 16f, Ui.muted))
        Ui.add(content, Ui.space(this, 26)); Ui.add(content, Ui.text(this, "把同款搭配放到桌面", 21f, Ui.ink, true)); Ui.add(content, Ui.space(this, 10))
        if (theme.widgets.isEmpty()) Ui.add(content, Ui.text(this, "该主题暂无组件", 15f, Ui.muted))
        WidgetConfigParser.kinds.forEach { (kind, label) -> if (kind in theme.widgets) {
            Ui.add(content, Ui.button(this, "添加${label}组件") { AlertDialog.Builder(this).setTitle("选择${label}组件尺寸").setItems(arrayOf("2×2", "4×2")) { _, which ->
                val message = WidgetPinManager(this).request(theme, kind, which == 1); AlertDialog.Builder(this).setTitle("添加${label}组件").setMessage(message).setPositiveButton("知道了", null).show()
            }.show() }); Ui.add(content, Ui.space(this, 10))
        } }
        Ui.add(content, Ui.space(this, 12)); Ui.add(content, Ui.button(this, "添加主题图标") { startActivity(Intent(this, ThemeIconsActivity::class.java).putExtra("theme_id", theme.id)) })
        Ui.add(content, Ui.space(this, 20)); Ui.add(content, Ui.text(this, "组件和图标需在桌面确认后添加，你可以稍后再来完成。应用状态记录本次操作，不会追踪之后手动更换的壁纸。", 13f, Ui.muted))
    }
}

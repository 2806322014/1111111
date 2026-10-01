package com.example.localthemeloader

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.*

class ThemeDetailActivity : ThemeScreenActivity() {
    private var tab = "lock"
    private var selection = ApplySelection()
    private var initialized = false
    private var applying: ThemeApplyOperation? = null
    private var applyDialog: AlertDialog? = null
    private val themeId get() = intent.getStringExtra("theme_id") ?: ""
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); tab = state?.getString("tab") ?: "lock"
        if (state != null) { selection = ApplySelection(state.getBoolean("home"), state.getBoolean("lock"), state.getBoolean("widgets"), state.getBoolean("icons")); initialized = true }
        applying = lastNonConfigurationInstance as? ThemeApplyOperation
        busy = applying != null; render()
        applying?.let { observe(it) }
    }
    @Deprecated("Native Activity retention supports Android 10+")
    override fun onRetainNonConfigurationInstance(): Any? = applying
    override fun onDestroy() { applying?.detach(); applyDialog?.dismiss(); super.onDestroy() }
    override fun onResume() { super.onResume(); if (!busy) render() }
    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); out.putString("tab", tab)
        out.putBoolean("home", selection.home); out.putBoolean("lock", selection.lock); out.putBoolean("widgets", selection.widgets); out.putBoolean("icons", selection.icons) }
    private fun render() {
        val theme = repository.byId(themeId) ?: run { toast("主题已被删除，请重新导入。"); finish(); return }
        if (!initialized) { selection = selection.copy(widgets = theme.widgets.isNotEmpty(), icons = false); initialized = true }
        val content = frame("主题详情", footer = "开始应用" to { apply(theme) })
        val title = Ui.row(this); title.addView(Ui.text(this, theme.name, 28f, Ui.ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        title.addView(Ui.button(this, "删除主题") { delete(theme) }, LinearLayout.LayoutParams(Ui.dp(this, 112), Ui.dp(this, 48)))
        Ui.add(content, title); Ui.add(content, Ui.space(this, 8)); Ui.add(content, Ui.text(this, "${theme.author}  ·  V${theme.version}\n占用空间：${Ui.mb(theme.bytes)}", 13f, Ui.muted))
        Ui.add(content, Ui.space(this, 16)); Ui.add(content, Ui.text(this, theme.description, 16f, Ui.muted)); Ui.add(content, Ui.space(this, 24))
        val tabs = Ui.row(this)
        listOf("lock" to "锁屏", "home" to "桌面", "widgets" to "组件", "icons" to "图标").forEach { (key, label) ->
            tabs.addView(Ui.button(this, label, tab == key) { tab = key; render() }, LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f).apply { marginEnd = Ui.dp(this@ThemeDetailActivity, 4) })
        }
        Ui.add(content, tabs); Ui.add(content, Ui.space(this, 16))
        val file = when (tab) { "home" -> theme.homePreview; "widgets" -> theme.widgetsPreview; "icons" -> theme.iconsPreview; else -> theme.lockPreview }
        if (file == null) { val box = Ui.card(this); Ui.padded(box, 28); Ui.add(box, Ui.text(this, if (tab == "widgets") "该主题暂无组件" else "该主题暂无图标预览", 18f, Ui.ink, true)); Ui.add(content, box) }
        else Ui.add(content, image(file, "${theme.name}效果预览", if (tab in setOf("widgets", "icons")) 280 else 360), if (tab in setOf("widgets", "icons")) 280 else 360)
        Ui.add(content, Ui.space(this, 10)); Ui.add(content, Ui.text(this, "效果图供搭配参考，系统时钟、桌面排列与确认窗口以手机为准。", 12f, Ui.muted))
        Ui.add(content, Ui.space(this, 24)); Ui.add(content, Ui.text(this, "应用主题", 21f, Ui.ink, true)); Ui.add(content, Ui.space(this, 8))
        fun check(label: String, checked: Boolean, enabled: Boolean = true, change: (Boolean) -> Unit) {
            Ui.add(content, CheckBox(this).apply { text = label; textSize = 16f; setTextColor(Ui.ink); minHeight = Ui.dp(context, 48); isChecked = checked; isEnabled = enabled; setOnCheckedChangeListener { _, value -> change(value) } })
        }
        check("锁屏壁纸", selection.lock) { selection = selection.copy(lock = it) }; check("桌面壁纸", selection.home) { selection = selection.copy(home = it) }
        check(if (theme.widgets.isEmpty()) "主题组件 · 该主题暂无组件" else "主题组件", selection.widgets, theme.widgets.isNotEmpty()) { selection = selection.copy(widgets = it) }
        check("主题图标", selection.icons) { selection = selection.copy(icons = it) }
        Ui.add(content, Ui.text(this, "主题图标以桌面快捷方式形式添加，不会修改系统原始图标。", 12f, Ui.muted))
        Ui.add(content, Ui.space(this, 24)); val compatibility = Ui.card(this); Ui.padded(compatibility, 18)
        Ui.add(compatibility, Ui.text(this, "本机兼容性", 18f, Ui.ink, true)); Ui.add(compatibility, Ui.space(this, 12)); Ui.add(compatibility, Ui.text(this, CompatibilityManager(this).detect().description(theme), 14f, Ui.muted))
        Ui.add(compatibility, Ui.space(this, 10)); Ui.add(compatibility, Ui.text(this, "手机系统没有单独检测锁屏设置的公开入口，实际是否成功会在应用后说明。", 12f, Ui.muted)); Ui.add(content, compatibility)
    }
    private fun apply(theme: ThemePackage) {
        if (busy) return
        val operation = ThemeApplyOperation(applicationContext); applying = operation; busy = true
        observe(operation); operation.start(theme, selection)
    }
    private fun observe(operation: ThemeApplyOperation) {
        applyDialog = AlertDialog.Builder(this).setMessage("正在应用主题…").setCancelable(false).create().also { it.show() }
        operation.attach { result, failure ->
            if (!isFinishing && !isDestroyed) {
                applying = null; busy = false; operation.detach(); applyDialog?.dismiss(); applyDialog = null
                if (failure != null) error(if (failure is ThemeException) failure.message ?: "暂时无法应用。" else "暂时无法应用，请稍后重试。")
                else if (result != null) startActivity(Intent(this, ThemeResultActivity::class.java).putExtra("theme_id", themeId).putStringArrayListExtra("messages", ArrayList(result.messages)))
            }
        }
    }
    private fun delete(theme: ThemePackage) {
        AlertDialog.Builder(this).setTitle("确定删除《${theme.name}》吗？").setMessage("将删除本机主题资源和保存的主题包。手机当前壁纸会保留；相关组件会显示默认样式，已添加的图标需在桌面自行移除。")
            .setNegativeButton("取消", null).setPositiveButton("删除") { _, _ -> background("正在删除主题…", { repository.delete(theme.id) }) { ok ->
                if (ok) { toast("主题已删除"); finish() } else error("部分资源暂时无法移除，请稍后重试。") } }.show()
    }
}

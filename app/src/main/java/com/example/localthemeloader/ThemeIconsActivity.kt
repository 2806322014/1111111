package com.example.localthemeloader

import android.os.Bundle
import android.widget.*

class ThemeIconsActivity : ThemeScreenActivity() {
    private lateinit var manager: ShortcutIconManager
    private var apps: List<LaunchableApp> = emptyList()
    private val selected = linkedSetOf<String>()
    private var queue: List<String> = emptyList()
    private var index = 0
    private var token = ""
    private var requested = 0
    private var confirmed = 0
    private var message = ""
    private val themeId get() = intent.getStringExtra("theme_id") ?: ""
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); manager = ShortcutIconManager(this)
        state?.getStringArrayList("selected")?.let { selected.addAll(it) }; queue = state?.getStringArrayList("queue") ?: emptyList()
        index = state?.getInt("index") ?: 0; token = state?.getString("token") ?: ""; requested = state?.getInt("requested") ?: 0; confirmed = state?.getInt("confirmed") ?: 0; message = state?.getString("message") ?: ""
        background("正在读取可添加的应用…", { manager.apps() }) { apps = it; render() }
    }
    override fun onResume() {
        super.onResume()
        if (::manager.isInitialized && apps.isNotEmpty() && !busy) {
            if (token.isNotEmpty() && manager.confirmed(token)) {
                confirmed++; manager.consume(token); token = ""; index++
                if (index >= queue.size) { queue = emptyList(); selected.clear(); message = "本轮添加已结束，已收到 $confirmed 次确认。" }
                else message = "已确认 $confirmed 枚；点击继续添加下一枚。"
            }; render()
        }
    }
    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out)
        out.putStringArrayList("selected", ArrayList(selected)); out.putStringArrayList("queue", ArrayList(queue)); out.putInt("index", index); out.putString("token", token)
        out.putInt("requested", requested); out.putInt("confirmed", confirmed); out.putString("message", message) }
    private fun render() {
        val theme = repository.byId(themeId) ?: run { toast("主题已删除，请重新导入。"); finish(); return }
        val hasQueue = queue.isNotEmpty() && index < queue.size
        val content = frame("主题图标", footer = (if (hasQueue) "继续添加下一枚" else "添加主题图标") to { addNext(theme) })
        Ui.add(content, Ui.text(this, theme.name, 28f, Ui.ink, true)); Ui.add(content, Ui.space(this, 12)); Ui.add(content, Ui.text(this, "主题图标以桌面快捷方式形式添加，不会修改系统原始图标。", 15f, Ui.muted)); Ui.add(content, Ui.space(this, 10))
        Ui.add(content, Ui.text(this, "每枚图标按手机桌面的要求确认。取消后，可继续下一枚；尚未收录的应用会保留原图标，搭配主题底色。", 13f, Ui.muted)); Ui.add(content, Ui.space(this, 16))
        if (message.isNotEmpty()) { Ui.add(content, Ui.text(this, message, 15f, Ui.green, true)); Ui.add(content, Ui.space(this, 12)) }
        if (hasQueue) { Ui.add(content, Ui.text(this, "当前进度 ${index + 1}/${queue.size} · 已发出 $requested 次请求", 14f, Ui.muted)); Ui.add(content, Ui.space(this, 12)) }
        val controls = Ui.row(this)
        controls.addView(Ui.button(this, "全选") { if (!hasQueue) { selected.addAll(apps.map { it.component.flattenToString() }); render() } }, LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f).apply { marginEnd = Ui.dp(this@ThemeIconsActivity, 8) })
        controls.addView(Ui.button(this, "取消全选") { if (!hasQueue) { selected.clear(); render() } }, LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f)); Ui.add(content, controls); Ui.add(content, Ui.space(this, 20))
        apps.forEach { app ->
            val row = Ui.row(this).apply { background = Ui.shape(android.graphics.Color.WHITE, Ui.dp(context, 18)); Ui.padded(this, 12) }
            row.addView(ImageView(this).apply { setImageDrawable(app.original); contentDescription = "${app.name}原图标" }, LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)))
            row.addView(Ui.text(this, app.name, 16f).apply { setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 8), 0) }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(ImageView(this).apply { setImageBitmap(manager.bitmap(theme, app)); contentDescription = "${app.name}主题图标预览" }, LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)))
            row.addView(CheckBox(this).apply { contentDescription = "选择${app.name}"; isChecked = app.component.flattenToString() in selected; isEnabled = !hasQueue; minWidth = Ui.dp(context, 48); minHeight = Ui.dp(context, 48)
                setOnCheckedChangeListener { _, checked -> if (checked) selected.add(app.component.flattenToString()) else selected.remove(app.component.flattenToString()) } }, LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)))
            Ui.add(content, row); Ui.add(content, Ui.space(this, 10))
        }
        if (apps.isEmpty()) Ui.add(content, Ui.text(this, "没有找到可添加的应用。", 16f, Ui.muted))
    }
    private fun addNext(theme: ThemePackage) {
        if (token.isNotEmpty()) {
            if (manager.confirmed(token)) confirmed++
            manager.consume(token); token = ""; index++
            if (index >= queue.size) { queue = emptyList(); selected.clear(); message = "本轮添加已结束，已发出 $requested 次请求，已收到 $confirmed 次确认。"; render(); return }
        }
        if (queue.isEmpty() || index >= queue.size) {
            if (selected.isEmpty()) { toast("请先选择要添加的应用。"); return }
            queue = selected.toList(); index = 0; requested = 0; confirmed = 0
        }
        val app = apps.firstOrNull { it.component.flattenToString() == queue[index] }
        if (app == null) { index++; message = "这个应用已移除，可继续下一枚。"; render(); return }
        val result = manager.request(theme, app); message = result.message
        if (result.accepted) { requested++; token = result.token } else { queue = emptyList(); index = 0; error(result.message) }; render()
    }
}

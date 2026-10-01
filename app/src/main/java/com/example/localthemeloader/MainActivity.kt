package com.example.localthemeloader

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*

class MainActivity : ThemeScreenActivity() {
    private var prepared: PreparedImport? = null
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); render(); accept(intent)
        val prefs = getSharedPreferences("shiguang", 0)
        if (!prefs.getBoolean("welcome_v2", false)) AlertDialog.Builder(this).setTitle("欢迎来到拾光").setMessage("导入喜欢的主题，选择壁纸，再添加同款桌面组件与图标快捷方式。\n\n主题只保存在本机，无需注册。")
            .setPositiveButton("开始拾光") { _, _ -> prefs.edit().putBoolean("welcome_v2", true).apply() }.show()
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); accept(intent) }
    override fun onResume() { super.onResume(); if (!busy) render() }
    override fun onDestroy() { prepared?.close(); super.onDestroy() }
    private fun accept(i: Intent?) { if (i?.action == Intent.ACTION_VIEW) { val uri = i.data; i.action = Intent.ACTION_MAIN; if (uri != null) importUri(uri) } }
    private fun render() {
        val content = frame("拾光主题", false, "＋ 导入主题" to { pick() })
        val top = Ui.row(this); top.addView(Ui.text(this, "我的主题", 32f, Ui.ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(Ui.button(this, "设置") { startActivity(Intent(this, SettingsActivity::class.java)) }, LinearLayout.LayoutParams(Ui.dp(this, 88), Ui.dp(this, 48)))
        Ui.add(content, top); Ui.add(content, Ui.space(this, 10)); Ui.add(content, Ui.text(this, "收藏一份喜欢，让每次点亮都有好心情。", 14f, Ui.muted)); Ui.add(content, Ui.space(this, 22))
        val themes = repository.list(); Ui.add(content, Ui.text(this, "${themes.size} 套主题  ·  ${Ui.mb(repository.bytes())}", 13f, Ui.muted)); Ui.add(content, Ui.space(this, 16))
        if (themes.isEmpty()) {
            val empty = Ui.card(this); Ui.padded(empty, 24)
            Ui.add(empty, Ui.text(this, "你的下一份喜欢", 25f, Ui.ink, true)); Ui.add(empty, Ui.space(this, 12)); Ui.add(empty, Ui.text(this, "导入 .ltheme 主题包，把壁纸、组件和图标搭配一起收藏。", 16f, Ui.muted))
            Ui.add(empty, Ui.space(this, 24)); Ui.add(empty, Ui.button(this, "导入第一套主题") { pick() }); Ui.add(content, empty)
        }
        val states = ThemeApplyStateRepository(this)
        themes.forEach { theme ->
            val card = Ui.card(this); Ui.add(card, image(theme.previewFile, "${theme.name}主题封面", 230, true), 230)
            val info = Ui.column(this); Ui.padded(info, 20)
            val row = Ui.row(this); row.addView(Ui.text(this, theme.name, 23f, Ui.ink, true), LinearLayout.LayoutParams(0, -2, 1f))
            val s = states.get(theme.id); val label = if (s != null && s.themeVersionCode != theme.versionCode) "待重新应用" else s?.label ?: "未应用"
            row.addView(Ui.text(this, label, 12f, Ui.green, true).apply { background = Ui.shape(Ui.soft, Ui.dp(context, 12)); Ui.padded(this, 8) })
            Ui.add(info, row); Ui.add(info, Ui.space(this, 8)); Ui.add(info, Ui.text(this, "${theme.author}  ·  V${theme.version}", 13f, Ui.muted)); Ui.add(info, Ui.space(this, 12))
            Ui.add(info, Ui.text(this, theme.description, 15f, Ui.muted)); Ui.add(info, Ui.space(this, 16)); Ui.add(info, Ui.button(this, "查看主题") { openDetail(theme.id) })
            Ui.add(card, info); Ui.add(content, card); Ui.add(content, Ui.space(this, 18))
        }
        Ui.add(content, Ui.text(this, "应用状态代表拾光最后一次执行记录，之后手动更换壁纸不会同步到这里。", 12f, Ui.muted))
    }
    private fun pick() { if (!busy) startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 1001) }
    @Deprecated("Supported on Android 10+")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data); if (requestCode == 1001 && resultCode == RESULT_OK) data?.data?.let { importUri(it) }
    }
    private fun importUri(uri: Uri) {
        background("正在检查主题…", { ThemeImporter(repository).prepare(uri) }) { incoming ->
            prepared?.close(); prepared = incoming; val old = incoming.previous
            if (old == null) commit(incoming)
            else if (incoming.theme.versionCode <= old.versionCode) { incoming.close(); prepared = null; toast("已收藏相同或更新版本，无需重复导入。"); openDetail(old.id) }
            else AlertDialog.Builder(this).setTitle("发现主题更新").setMessage("${incoming.theme.name}\n${old.version} → ${incoming.theme.version}\n\n新主题已完整检查，确认后替换本机收藏。当前壁纸不会自动更换。")
                .setNegativeButton("保留旧版") { _, _ -> incoming.close(); prepared = null }.setPositiveButton("更新主题") { _, _ -> commit(incoming) }
                .setOnCancelListener { incoming.close(); prepared = null }.show()
        }
    }
    private fun commit(incoming: PreparedImport) {
        background("正在保存主题…", { try { ThemeImporter(repository).commit(incoming) } finally { incoming.close() } }) { theme ->
            prepared = null; render(); toast("${theme.name}已收藏到主题库"); openDetail(theme.id)
        }
    }
}

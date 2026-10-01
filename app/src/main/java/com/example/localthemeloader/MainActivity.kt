package com.example.localthemeloader

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** An empty local library until the user imports a package. No theme resources are bundled. */
class MainActivity : ThemeScreenActivity() {
    private var prepared: PreparedImport? = null
    private var themes = emptyList<ThemePackage>()
    private var selectedId: String? = null
    private var selection = ApplySelection(widgets = false)
    private var query = ""
    private var sortByName = false
    private var page = 1
    private var applying: ThemeApplyOperation? = null
    private var applyingId: String? = null
    private var applyDialog: AlertDialog? = null
    private lateinit var library: LinearLayout
    private lateinit var libraryCount: TextView
    private val selected get() = themes.firstOrNull { it.id == selectedId }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val prefs = getSharedPreferences("local_library_ui", 0)
        selectedId = state?.getString("selected") ?: prefs.getString("selected", null)
        page = state?.getInt("page", 1) ?: 1; query = state?.getString("query") ?: ""
        sortByName = state?.getBoolean("sort") ?: false
        if (state != null) selection = ApplySelection(state.getBoolean("home", true), state.getBoolean("lock", true), state.getBoolean("widgets"), state.getBoolean("icons"))
        applying = lastNonConfigurationInstance as? ThemeApplyOperation
        applyingId = state?.getString("applying_id"); busy = applying != null
        reload(state == null); render(); applying?.let { observe(it) }; accept(intent)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); accept(intent) }
    override fun onResume() { super.onResume(); if (!busy) { reload(false); render() } }
    override fun onDestroy() { prepared?.close(); applying?.detach(); applyDialog?.dismiss(); super.onDestroy() }
    @Deprecated("Native Activity retention supports Android 10+")
    override fun onRetainNonConfigurationInstance(): Any? = applying
    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out); out.putString("selected", selectedId); out.putInt("page", page)
        out.putString("query", query); out.putBoolean("sort", sortByName); out.putString("applying_id", applyingId)
        out.putBoolean("home", selection.home); out.putBoolean("lock", selection.lock)
        out.putBoolean("widgets", selection.widgets); out.putBoolean("icons", selection.icons)
    }
    private fun reload(first: Boolean) {
        themes = repository.list()
        if (selected == null) {
            selectedId = themes.firstOrNull()?.id
            selection = ApplySelection(widgets = selected?.widgets?.isNotEmpty() == true)
        } else if (first) selection = ApplySelection(widgets = selected?.widgets?.isNotEmpty() == true)
        if (selected?.widgets.isNullOrEmpty()) selection = selection.copy(widgets = false)
    }
    private fun choose(theme: ThemePackage) {
        if (busy) return
        if (selectedId != theme.id) selection = ApplySelection(widgets = theme.widgets.isNotEmpty())
        selectedId = theme.id
        if (theme.widgets.isEmpty()) selection = selection.copy(widgets = false)
        getSharedPreferences("local_library_ui", 0).edit().putString("selected", selectedId).apply()
        hideKeyboard(); render()
    }
    private fun hideKeyboard() { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(window.decorView.windowToken, 0) }
    private fun accept(i: Intent?) {
        if (i?.action == Intent.ACTION_VIEW) { val uri = i.data; i.action = Intent.ACTION_MAIN; if (uri != null) importUri(uri) }
    }
    private fun render() {
        val root = Ui.column(this).apply { setBackgroundColor(Ui.bg) }
        Ui.setup(this, root)
        val content = Ui.column(this).apply { setPadding(Ui.dp(context, 16), Ui.dp(context, 12), Ui.dp(context, 16), Ui.dp(context, 18)) }
        Ui.add(root, ScrollView(this).apply { addView(content); clipToPadding = false }, 0, 1f)
        header(content)
        when (page) { 0 -> home(content); 2 -> mine(content); else -> themePage(content) }
        if (page != 2) {
            val bar = Ui.column(this).apply { setPadding(Ui.dp(context, 16), Ui.dp(context, 6), Ui.dp(context, 16), Ui.dp(context, 8)) }
            val label = if (selected == null) "导入主题后即可应用" else "立即应用主题"
            Ui.add(bar, Ui.button(this, label, true) { selected?.let { apply(it) } }.apply {
                tag = "apply_theme"; isEnabled = selected != null && !busy; alpha = if (isEnabled) 1f else 0.48f; textSize = 19f
            }, 56)
            Ui.add(root, bar)
        }
        navigation(root)
    }
    private fun header(parent: LinearLayout) {
        val row = Ui.row(this)
        row.addView(Ui.text(this, when (page) { 0 -> "拾光主题"; 2 -> "我的拾光"; else -> "本地主题" }, 27f, Ui.ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Ui.iconButton(this, R.drawable.ic_history, "应用记录") { history() }, LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)))
        row.addView(Ui.iconButton(this, R.drawable.ic_settings, "设置") { startActivity(Intent(this, SettingsActivity::class.java)) }, LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)))
        Ui.add(parent, row); Ui.add(parent, Ui.text(this, if (page == 2) "本机收藏与应用管理" else "导入本地主题，打造专属桌面", 14f, Ui.muted))
        Ui.add(parent, Ui.space(this, 12))
    }
    private fun themePage(parent: LinearLayout) {
        searchAndImport(parent); Ui.add(parent, Ui.space(this, 12)); hero(parent)
        Ui.add(parent, Ui.space(this, 14)); applicationChoices(parent)
        Ui.add(parent, Ui.space(this, 14)); librarySection(parent)
    }
    private fun searchAndImport(parent: LinearLayout) {
        val row = Ui.row(this)
        val search = Ui.row(this).apply { background = Ui.outline(this@MainActivity, radius = 16); setPadding(Ui.dp(context, 10), 0, Ui.dp(context, 8), 0) }
        search.addView(Ui.icon(this, R.drawable.ic_search, Ui.muted, 20))
        val input = EditText(this).apply {
            tag = "theme_search"; setSingleLine(true); hint = "搜索主题或作者"; textSize = 14f; setTextColor(Ui.ink); setHintTextColor(Ui.muted)
            setPadding(Ui.dp(context, 8), 0, 0, 0); background = null; setText(query)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, _, _ -> hideKeyboard(); clearFocus(); true }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s?.toString() ?: ""; if (::library.isInitialized) populateLibrary() }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        search.addView(input, LinearLayout.LayoutParams(0, -1, 1f))
        val importButton = Ui.button(this, "导入主题包", true) { pick() }.apply {
            tag = "import_theme"; textSize = 14f; setSingleLine(true); setPadding(Ui.dp(context, 8), 0, Ui.dp(context, 8), 0)
            setCompoundDrawablesRelativeWithIntrinsicBounds(getDrawable(R.drawable.ic_import)?.apply { setTint(Color.WHITE) }, null, null, null); compoundDrawablePadding = Ui.dp(context, 4)
        }
        if (resources.configuration.screenWidthDp < 360 || resources.configuration.fontScale > 1.15f) {
            row.orientation = LinearLayout.VERTICAL; Ui.add(row, search, 48); Ui.add(row, Ui.space(this, 8)); Ui.add(row, importButton, 48)
        } else {
            row.addView(search, LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f).apply { marginEnd = Ui.dp(this@MainActivity, 10) })
            row.addView(importButton, LinearLayout.LayoutParams(Ui.dp(this, 132), Ui.dp(this, 48)))
        }
        Ui.add(parent, row)
    }
    private fun hero(parent: LinearLayout) {
        val theme = selected
        val card = FrameLayout(this).apply { background = Ui.gradient(this@MainActivity, 24, true); clipToOutline = true; tag = "theme_hero" }
        val layout = Ui.row(this).apply { setPadding(Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12)) }
        if (theme != null) {
            layout.addView(image(theme.lockPreview ?: theme.previewFile, "${theme.name}主题预览", 206, true).apply { background = Ui.outline(this@MainActivity, radius = 18); elevation = Ui.dp(context, 2).toFloat(); tag = "imported_preview" },
                LinearLayout.LayoutParams(0, Ui.dp(this, (196 * resources.configuration.fontScale.coerceAtLeast(1f)).toInt()), 0.45f).apply { marginEnd = Ui.dp(this@MainActivity, 14) })
        } else {
            val symbol = FrameLayout(this).apply { background = Ui.shape(0xBAFFFFFF.toInt(), Ui.dp(context, 24)) }
            symbol.addView(Ui.icon(this, R.drawable.ic_folder, Ui.green, 60), FrameLayout.LayoutParams(Ui.dp(this, 60), Ui.dp(this, 60), Gravity.CENTER))
            layout.addView(symbol, LinearLayout.LayoutParams(0, Ui.dp(this, 140), 0.4f).apply { marginEnd = Ui.dp(this@MainActivity, 14) })
        }
        val copy = Ui.column(this).apply { gravity = Gravity.CENTER_VERTICAL }
        Ui.add(copy, Ui.text(this, theme?.name ?: "导入你的主题", 21f, Ui.ink, true).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
        Ui.add(copy, Ui.space(this, 6)); Ui.add(copy, Ui.text(this, theme?.author?.let { "$it · V${theme.version}" } ?: "软件不内置主题或壁纸", 12f, Ui.muted).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        Ui.add(copy, Ui.space(this, 10))
        listOf(Triple(R.drawable.ic_folder, "本地导入", "收藏自己的主题包"), Triple(R.drawable.ic_bolt, "一键应用", "桌面与锁屏壁纸"), Triple(R.drawable.ic_offline, "离线使用", "无需登录与网络")).forEach { (icon, title, subtitle) ->
            val feature = Ui.row(this).apply { background = Ui.shape(0xD9FFFFFF.toInt(), Ui.dp(context, 18)); setPadding(Ui.dp(context, 9), Ui.dp(context, 5), Ui.dp(context, 8), Ui.dp(context, 5)) }
            feature.addView(Ui.icon(this, icon, if (icon == R.drawable.ic_folder) Ui.green else Color.rgb(26, 139, 133), 22), LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22)).apply { marginEnd = Ui.dp(this@MainActivity, 8) })
            val words = Ui.column(this); Ui.add(words, Ui.text(this, title, 13f, Ui.ink, true)); Ui.add(words, Ui.text(this, subtitle, 11f, Ui.muted).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            feature.addView(words, LinearLayout.LayoutParams(0, -2, 1f)); Ui.add(copy, feature); Ui.add(copy, Ui.space(this, 4))
        }
        layout.addView(copy, LinearLayout.LayoutParams(0, -2, if (theme == null) 0.6f else 0.55f)); card.addView(layout, FrameLayout.LayoutParams(-1, -2))
        if (theme != null) { card.contentDescription = "查看${theme.name}主题详情"; card.setOnClickListener { if (!busy) openDetail(theme.id) } }
        Ui.add(parent, card)
    }
    private fun applicationChoices(parent: LinearLayout) {
        val heading = Ui.row(this); heading.addView(Ui.text(this, "应用内容", 19f, Ui.ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(Ui.text(this, "可自由选择", 12f, Ui.muted)); Ui.add(parent, heading); Ui.add(parent, Ui.space(this, 10))
        val row = Ui.row(this); val theme = selected
        fun tile(title: String, subtitle: String, icon: Int, enabled: Boolean, checked: Boolean, tagName: String, change: () -> Unit) {
            val box = Ui.column(this).apply { tag = tagName; setPadding(Ui.dp(context, 10), Ui.dp(context, 10), Ui.dp(context, 8), Ui.dp(context, 10))
                background = Ui.outline(this@MainActivity, enabled && checked); elevation = Ui.dp(context, 1).toFloat()
                isEnabled = enabled; alpha = if (enabled) 1f else 0.5f
                contentDescription = "$title，$subtitle，${if (!enabled) "暂不可选" else if (checked) "已选择" else "未选择"}"
                accessibilityDelegate = object : View.AccessibilityDelegate() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) { super.onInitializeAccessibilityNodeInfo(host, info); info.className = CheckBox::class.java.name; info.isCheckable = true; info.isChecked = enabled && checked }
                }
                setOnClickListener { if (enabled && !busy) { change(); render() } }
            }
            val top = Ui.row(this); top.addView(Ui.icon(this, icon, if (icon == R.drawable.ic_icons) Ui.purple else Ui.green, 25), LinearLayout.LayoutParams(0, Ui.dp(this, 25), 1f).apply { gravity = Gravity.START })
            top.addView(Ui.icon(this, R.drawable.ic_check, if (enabled && checked) Color.WHITE else Ui.border, 15).apply { background = Ui.shape(if (enabled && checked) Ui.green else Color.TRANSPARENT, Ui.dp(context, 12)) }, LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20)))
            Ui.add(box, top); Ui.add(box, Ui.space(this, 8)); Ui.add(box, Ui.text(this, title, 14f, Ui.ink, true)); Ui.add(box, Ui.text(this, subtitle, 11f, Ui.muted))
            row.addView(box, LinearLayout.LayoutParams(0, -2, 1f).apply { if (row.childCount > 0) marginStart = Ui.dp(this@MainActivity, 8) })
        }
        tile("壁纸", "桌面与锁屏", R.drawable.ic_wallpaper, theme != null, selection.home || selection.lock, "select_wallpapers") { selection = selection.copy(home = !selection.home && !selection.lock, lock = !selection.home && !selection.lock) }
        tile("桌面组件", if (theme != null && theme.widgets.isEmpty()) "该主题暂无组件" else "时钟 · 日期等", R.drawable.ic_widgets, theme?.widgets?.isNotEmpty() == true, selection.widgets, "select_widgets") { selection = selection.copy(widgets = !selection.widgets) }
        tile("图标快捷方式", "需在桌面确认", R.drawable.ic_icons, theme != null, selection.icons, "select_icons") { selection = selection.copy(icons = !selection.icons) }
        Ui.add(parent, row)
    }
    private fun librarySection(parent: LinearLayout) {
        val heading = Ui.row(this); heading.addView(Ui.text(this, "本地主题库", 20f, Ui.ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(Ui.button(this, if (sortByName) "按名称" else "最近导入") { sortByName = !sortByName; render() }.apply { textSize = 12f; background = null; minHeight = Ui.dp(context, 44); setPadding(0, 0, 0, 0) }, LinearLayout.LayoutParams(Ui.dp(this, 92), Ui.dp(this, 44)))
        Ui.add(parent, heading)
        libraryCount = Ui.text(this, "", 12f, Ui.muted); Ui.add(parent, libraryCount); Ui.add(parent, Ui.space(this, 10))
        library = Ui.column(this).apply { tag = "theme_library" }; Ui.add(parent, library); populateLibrary()
    }
    private fun populateLibrary() {
        library.removeAllViews()
        val filtered = themes.filter { it.name.contains(query.trim(), true) || it.author.contains(query.trim(), true) }
            .let { if (sortByName) it.sortedBy { theme -> theme.name } else it }
        libraryCount.text = if (query.isBlank()) "${themes.size} 套主题 · ${Ui.mb(repository.bytes())}" else "找到 ${filtered.size} 套主题"
        if (filtered.isEmpty()) {
            val empty = Ui.card(this).apply { Ui.padded(this, 18) }
            Ui.add(empty, Ui.text(this, if (themes.isEmpty()) "还没有导入主题" else "没有找到相关主题", 17f, Ui.ink, true))
            Ui.add(empty, Ui.space(this, 6)); Ui.add(empty, Ui.text(this, if (themes.isEmpty()) "导入后，你的主题会保存在这里。" else "试试其他名称或作者。", 13f, Ui.muted)); Ui.add(library, empty); return
        }
        val strip = Ui.row(this).apply { gravity = Gravity.TOP }
        val width = ((resources.displayMetrics.widthPixels / resources.displayMetrics.density - 56) / 4).toInt().coerceIn(84, 126)
        filtered.forEach { theme ->
            val item = Ui.column(this).apply { tag = "theme_${theme.id}"; contentDescription = "选择${theme.name}"; setOnClickListener { choose(theme) } }
            val cover = FrameLayout(this).apply { background = Ui.outline(this@MainActivity, theme.id == selectedId, 14); clipToOutline = true; setPadding(Ui.dp(context, 3), Ui.dp(context, 3), Ui.dp(context, 3), Ui.dp(context, 3)) }
            cover.addView(image(theme.previewFile, "${theme.name}封面", 124, true, 384), FrameLayout.LayoutParams(-1, -1))
            cover.addView(Ui.iconButton(this, R.drawable.ic_more, "${theme.name}选项") { themeMenu(theme) }.apply { background = Ui.shape(0xDBFFFFFF.toInt(), Ui.dp(context, 14)) }, FrameLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44), Gravity.BOTTOM or Gravity.END))
            if (theme.id == selectedId) cover.addView(Ui.icon(this, R.drawable.ic_check, Color.WHITE, 18).apply { background = Ui.shape(Ui.green, Ui.dp(context, 12)); setPadding(3, 3, 3, 3) }, FrameLayout.LayoutParams(Ui.dp(this, 23), Ui.dp(this, 23), Gravity.TOP or Gravity.END).apply { topMargin = Ui.dp(this@MainActivity, 5); marginEnd = Ui.dp(this@MainActivity, 5) })
            Ui.add(item, cover, 118); Ui.add(item, Ui.space(this, 5))
            Ui.add(item, Ui.text(this, theme.name, 13f, Ui.ink, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            Ui.add(item, Ui.text(this, Ui.mb(theme.bytes), 11f, Ui.muted))
            val record = ThemeApplyStateRepository(this).get(theme.id)
            Ui.add(item, Ui.text(this, if (record != null && record.themeVersionCode != theme.versionCode) "待重新应用" else record?.label ?: "未应用", 11f, Ui.green))
            strip.addView(item, LinearLayout.LayoutParams(Ui.dp(this, width), -2).apply { if (strip.childCount > 0) marginStart = Ui.dp(this@MainActivity, 8) })
        }
        Ui.add(library, HorizontalScrollView(this).apply { addView(strip); isHorizontalScrollBarEnabled = false })
    }
    private fun themeMenu(theme: ThemePackage) {
        AlertDialog.Builder(this).setTitle(theme.name).setItems(arrayOf("查看主题详情", "选中并应用", "删除主题")) { _, which -> when (which) {
            0 -> openDetail(theme.id); 1 -> { choose(theme); apply(theme) }; 2 -> delete(theme)
        } }.show()
    }
    private fun delete(theme: ThemePackage) {
        AlertDialog.Builder(this).setTitle("确定删除《${theme.name}》吗？").setMessage("删除本机主题资源与原始主题包，手机当前壁纸会保留。相关组件会显示默认样式，快捷方式需在桌面自行移除。")
            .setNegativeButton("取消", null).setPositiveButton("删除") { _, _ -> background("正在删除主题…", { repository.delete(theme.id) }) { ok ->
                if (ok) { reload(false); render(); toast("主题已删除") } else error("部分资源暂时无法移除，请稍后重试。") } }.show()
    }
    private fun home(parent: LinearLayout) {
        hero(parent); Ui.add(parent, Ui.space(this, 18))
        val card = Ui.card(this).apply { Ui.padded(this, 18) }; Ui.add(card, Ui.text(this, "把喜欢，留在每一次点亮", 20f, Ui.ink, true))
        Ui.add(card, Ui.space(this, 10)); Ui.add(card, Ui.text(this, "1  导入本地 .ltheme 主题包\n2  预览主题并选择应用内容\n3  应用壁纸，确认添加组件与图标", 14f, Ui.muted))
        Ui.add(card, Ui.space(this, 16)); Ui.add(card, Ui.button(this, "导入本地主题包", true) { pick() }); Ui.add(parent, card)
        Ui.add(parent, Ui.space(this, 18)); applicationChoices(parent); Ui.add(parent, Ui.space(this, 18)); librarySection(parent)
    }
    private fun mine(parent: LinearLayout) {
        val card = Ui.card(this).apply { Ui.padded(this, 22) }; Ui.add(card, Ui.text(this, "只收藏你导入的喜欢", 23f, Ui.ink, true)); Ui.add(card, Ui.space(this, 12))
        Ui.add(card, Ui.text(this, "${themes.size} 套本地主题\n资源占用 ${Ui.mb(repository.bytes())}", 16f, Ui.muted)); Ui.add(parent, card); Ui.add(parent, Ui.space(this, 20))
        Ui.add(parent, Ui.button(this, "应用记录") { history() }); Ui.add(parent, Ui.space(this, 12)); Ui.add(parent, Ui.button(this, "存储与应用状态设置") { startActivity(Intent(this, SettingsActivity::class.java)) })
        Ui.add(parent, Ui.space(this, 24)); Ui.add(parent, Ui.text(this, "拾光主题 ${BuildConfig.VERSION_NAME}\n\n无需登录，主题只保存在本机。软件没有内置主题、壁纸或在线商城。卸载会清除本地收藏，请保留原始主题包。", 14f, Ui.muted))
    }
    private fun navigation(root: LinearLayout) {
        val nav = Ui.row(this).apply { setBackgroundColor(Color.WHITE); setPadding(Ui.dp(context, 10), Ui.dp(context, 2), Ui.dp(context, 10), Ui.dp(context, 2)) }
        listOf(Triple(R.drawable.ic_home, "首页", 0), Triple(R.drawable.ic_themes, "主题", 1), Triple(R.drawable.ic_person, "我的", 2)).forEach { (resource, label, index) ->
            val entry = Ui.column(this).apply { gravity = Gravity.CENTER; contentDescription = label; tag = "nav_$index"; isSelected = page == index
                setOnClickListener { if (!busy) { hideKeyboard(); page = index; render() } } }
            entry.addView(Ui.icon(this, resource, if (page == index) Ui.green else Ui.muted, 25)); entry.addView(Ui.text(this, label, 12f, if (page == index) Ui.green else Ui.muted, page == index).apply { gravity = Gravity.CENTER })
            nav.addView(entry, LinearLayout.LayoutParams(0, Ui.dp(this, 60), 1f))
        }
        Ui.add(root, nav)
    }
    private fun history() {
        val repo = ThemeApplyStateRepository(this)
        val records = themes.mapNotNull { theme -> repo.get(theme.id)?.let { theme to it } }.sortedByDescending { it.second.appliedAt }
        if (records.isEmpty()) { AlertDialog.Builder(this).setTitle("应用记录").setMessage("还没有应用记录。导入主题后，选择内容即可应用。\n\n这里记录拾光最后一次执行结果。").setPositiveButton("知道了", null).show(); return }
        val format = SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA)
        AlertDialog.Builder(this).setTitle("最近应用记录").setItems(records.map { (theme, record) -> "${theme.name} · ${record.label}\n${format.format(Date(record.appliedAt))}" }.toTypedArray()) { _, index -> openDetail(records[index].first.id) }
            .setNegativeButton("关闭", null).show()
    }
    private fun apply(theme: ThemePackage) {
        if (busy) return
        hideKeyboard(); applyingId = theme.id
        val operation = ThemeApplyOperation(applicationContext); applying = operation; busy = true
        observe(operation); operation.start(theme, selection)
    }
    private fun observe(operation: ThemeApplyOperation) {
        applyDialog = AlertDialog.Builder(this).setMessage("正在应用主题…").setCancelable(false).create().also { it.show() }
        operation.attach { result, failure -> if (!isFinishing && !isDestroyed) {
            val id = applyingId; applying = null; applyingId = null; busy = false; operation.detach(); applyDialog?.dismiss(); applyDialog = null
            if (failure != null) error(if (failure is ThemeException) failure.message ?: "暂时无法应用。" else "暂时无法应用，请稍后重试。")
            else if (result != null && id != null) startActivity(Intent(this, ThemeResultActivity::class.java).putExtra("theme_id", id).putStringArrayListExtra("messages", ArrayList(result.messages)))
        } }
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
            else if (incoming.theme.versionCode <= old.versionCode) { incoming.close(); prepared = null; toast("已收藏相同或更新版本，无需重复导入。"); choose(old) }
            else AlertDialog.Builder(this).setTitle("发现主题更新").setMessage("${incoming.theme.name}\n${old.version} → ${incoming.theme.version}\n\n新主题已完整检查，确认后替换本机收藏。当前壁纸不会自动更换。")
                .setNegativeButton("保留旧版") { _, _ -> incoming.close(); prepared = null }.setPositiveButton("更新主题") { _, _ -> commit(incoming) }
                .setOnCancelListener { incoming.close(); prepared = null }.show()
        }
    }
    private fun commit(incoming: PreparedImport) {
        background("正在保存主题…", { try { ThemeImporter(repository).commit(incoming) } finally { incoming.close() } }) { theme ->
            prepared = null; query = ""; page = 1; themes = repository.list(); choose(theme); toast("${theme.name}已导入，可直接应用")
        }
    }
}

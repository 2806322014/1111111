package com.example.localthemeloader

import android.app.Activity
import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    companion object {
        private const val REQ_IMPORT = 1001
        private const val REQ_HOME = 1002
    }

    private lateinit var themeManager: ThemeManager
    private lateinit var statusText: TextView
    private lateinit var selectedThemeText: TextView
    private lateinit var lockPreview: ImageView
    private lateinit var homePreview: ImageView
    private lateinit var themeGrid: GridView
    private lateinit var themeAdapter: ThemeGridAdapter
    private lateinit var appGrid: GridView
    private lateinit var appAdapter: AppGridAdapter

    private var apps: List<ResolveInfo> = emptyList()
    private var themes: List<ThemePackage> = emptyList()
    private var selectedTheme: ThemePackage? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        themeManager = ThemeManager(this)
        statusText = findViewById(R.id.statusText)
        selectedThemeText = findViewById(R.id.selectedThemeText)
        lockPreview = findViewById(R.id.lockPreview)
        homePreview = findViewById(R.id.homePreview)
        themeGrid = findViewById(R.id.themeGrid)
        appGrid = findViewById(R.id.appGrid)

        themeAdapter = ThemeGridAdapter(this, emptyList(), null)
        themeGrid.adapter = themeAdapter

        appAdapter = AppGridAdapter(this, emptyList(), themeManager.currentTheme())
        appGrid.adapter = appAdapter

        findViewById<Button>(R.id.importButton).setOnClickListener { importTheme() }
        findViewById<Button>(R.id.applyButton).setOnClickListener { applySelectedTheme() }
        findViewById<Button>(R.id.deleteButton).setOnClickListener { deleteSelectedTheme() }
        findViewById<Button>(R.id.homeButton).setOnClickListener { requestHomeRole() }
        findViewById<Button>(R.id.packagesButton).setOnClickListener { copyPackages() }

        themeGrid.setOnItemClickListener { _, _, position, _ ->
            selectedTheme = themes[position]
            themeManager.selectTheme(selectedTheme!!.id)
            refreshThemeSelection()
        }

        appGrid.setOnItemClickListener { _, _, position, _ -> launch(apps[position]) }
        refreshAll()
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    private fun refreshAll() {
        apps = loadApps()
        themes = themeManager.listThemes()
        selectedTheme = themeManager.currentTheme() ?: themes.firstOrNull()

        if (selectedTheme != null && themeManager.currentTheme() == null) {
            themeManager.selectTheme(selectedTheme!!.id)
        }

        appAdapter.update(apps, selectedTheme)
        themeAdapter.update(themes, selectedTheme?.id)
        refreshThemeSelection()
    }

    private fun refreshThemeSelection() {
        val theme = selectedTheme
        if (theme == null) {
            statusText.text = "还没有导入主题"
            selectedThemeText.text = "点击“导入主题”添加你的第一套主题"
            lockPreview.setImageDrawable(null)
            homePreview.setImageDrawable(null)
            themeAdapter.update(themes, null)
            appAdapter.update(apps, null)
            return
        }

        statusText.text = "已保存 ${themes.size} 套主题" +
            if (isHomeRoleHeld()) " · 已是默认桌面" else " · 尚未设为默认桌面"

        selectedThemeText.text = theme.name +
            if (theme.author.isBlank()) "" else " · " + theme.author

        showPreview(lockPreview, theme.lockWallpaper ?: theme.previewFile)
        showPreview(homePreview, theme.homeWallpaper ?: theme.previewFile)

        themeAdapter.update(themes, theme.id)
        appAdapter.update(apps, theme)
    }

    private fun showPreview(view: ImageView, file: java.io.File?) {
        if (file != null && file.exists()) {
            view.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
        } else {
            view.setImageDrawable(null)
        }
    }

    private fun loadApps(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
    }

    private fun importTheme() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }
        startActivityForResult(intent, REQ_IMPORT)
    }

    @Deprecated("Deprecated in Android SDK, retained for broad OEM compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_IMPORT && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return
            try {
                val theme = themeManager.importTheme(uri)
                selectedTheme = theme
                Toast.makeText(this, "主题已保存到软件：" + theme.name, Toast.LENGTH_SHORT).show()
                refreshAll()
            } catch (e: Exception) {
                Toast.makeText(this, "导入失败：" + e.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun applySelectedTheme() {
        val theme = selectedTheme
        if (theme == null) {
            Toast.makeText(this, "请先选择或导入主题", Toast.LENGTH_SHORT).show()
            return
        }

        themeManager.selectTheme(theme.id)
        try {
            val messages = themeManager.applyWallpapers(theme).toMutableList()
            if (!isHomeRoleHeld()) {
                messages += "确认默认桌面后，主题图标才会生效"
                requestHomeRole()
            } else {
                messages += "主题图标已在本地主题桌面生效"
            }
            Toast.makeText(this, messages.joinToString("\n"), Toast.LENGTH_LONG).show()
            refreshAll()
        } catch (e: Exception) {
            Toast.makeText(this, "应用失败：" + e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun deleteSelectedTheme() {
        val theme = selectedTheme
        if (theme == null) {
            Toast.makeText(this, "没有可删除的主题", Toast.LENGTH_SHORT).show()
            return
        }

        val name = theme.name
        if (themeManager.deleteTheme(theme.id)) {
            Toast.makeText(this, "已删除：" + name, Toast.LENGTH_SHORT).show()
            refreshAll()
        } else {
            Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isHomeRoleHeld(): Boolean {
        val rm = getSystemService(RoleManager::class.java)
        return rm.isRoleAvailable(RoleManager.ROLE_HOME) && rm.isRoleHeld(RoleManager.ROLE_HOME)
    }

    private fun requestHomeRole() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
            if (rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                Toast.makeText(this, "已经是默认桌面", Toast.LENGTH_SHORT).show()
            } else {
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_HOME)
            }
        } else {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        }
    }

    private fun copyPackages() {
        val text = apps.joinToString("\n") {
            "${it.loadLabel(packageManager)} = ${it.activityInfo.packageName}"
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("应用包名", text))
        Toast.makeText(this, "已复制 ${apps.size} 个应用包名", Toast.LENGTH_SHORT).show()
    }

    private fun launch(info: ResolveInfo) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(info.activityInfo.packageName, info.activityInfo.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开该应用", Toast.LENGTH_SHORT).show()
        }
    }
}

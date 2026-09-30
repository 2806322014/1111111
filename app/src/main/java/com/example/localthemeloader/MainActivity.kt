package com.example.localthemeloader

import android.app.Activity
import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.GridView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    companion object {
        private const val REQ_IMPORT = 1001
        private const val REQ_HOME = 1002
    }

    private lateinit var themeManager: ThemeManager
    private lateinit var statusText: TextView
    private lateinit var grid: GridView
    private lateinit var adapter: AppGridAdapter
    private var apps: List<ResolveInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        themeManager = ThemeManager(this)
        statusText = findViewById(R.id.statusText)
        grid = findViewById(R.id.appGrid)
        adapter = AppGridAdapter(this, emptyList(), themeManager.currentTheme())
        grid.adapter = adapter

        findViewById<Button>(R.id.importButton).setOnClickListener { importTheme() }
        findViewById<Button>(R.id.applyButton).setOnClickListener { applyTheme() }
        findViewById<Button>(R.id.homeButton).setOnClickListener { requestHomeRole() }
        findViewById<Button>(R.id.packagesButton).setOnClickListener { copyPackages() }

        grid.setOnItemClickListener { _, _, position, _ -> launch(apps[position]) }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        apps = loadApps()
        val theme = themeManager.currentTheme()
        adapter.update(apps, theme)
        statusText.text = buildString {
            if (theme == null) append("还没有导入主题")
            else append("当前主题：${theme.name}${if (theme.author.isNotBlank()) " · ${theme.author}" else ""}")
            append(if (isHomeRoleHeld()) " · 已是默认桌面" else " · 尚未设为默认桌面")
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
            type = "*/*"
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
                Toast.makeText(this, "已导入：${theme.name}", Toast.LENGTH_SHORT).show()
                refresh()
            } catch (e: Exception) {
                Toast.makeText(this, "导入失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun applyTheme() {
        val theme = themeManager.currentTheme()
        if (theme == null) {
            Toast.makeText(this, "请先导入主题包", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val messages = themeManager.applyWallpapers(theme).toMutableList()
            if (!isHomeRoleHeld()) {
                messages += "再确认一次默认桌面，图标主题才会生效"
                requestHomeRole()
            } else {
                messages += "主题图标已在本桌面生效"
            }
            Toast.makeText(this, messages.joinToString("\n"), Toast.LENGTH_LONG).show()
            refresh()
        } catch (e: Exception) {
            Toast.makeText(this, "应用失败：${e.message}", Toast.LENGTH_LONG).show()
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

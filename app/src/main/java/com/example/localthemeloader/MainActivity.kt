package com.example.localthemeloader

import android.app.*
import android.app.role.RoleManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import java.io.File
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var manager: ThemeManager
    private val worker = Executors.newSingleThreadExecutor()
    private var page = "library"
    private var detailId: String? = null
    private var previewKind = "lock"
    private var busy = false
    private var pendingRole = false
    private var progressDialog: AlertDialog? = null
    private lateinit var root: LinearLayout

    override fun onCreate(state: Bundle?) {
        super.onCreate(state); manager = ThemeManager(applicationContext)
        detailId = state?.getString("detail"); page = state?.getString("page") ?: "library"
        previewKind = state?.getString("preview") ?: "lock"; pendingRole = state?.getBoolean("role") ?: false
        render()
        if (!manager.hasWelcome()) {
            AlertDialog.Builder(this).setTitle("欢迎来到拾光").setMessage("把喜欢的风景，放进每天。\n\n1  导入一个主题包\n2  预览锁屏、桌面与图标\n3  一键应用\n\n首次使用主题图标时，需要在系统弹窗中选择拾光桌面。主题只保存在本机，无需注册。")
                .setPositiveButton("开始拾光") { _, _ -> manager.dismissWelcome() }.setCancelable(false).show()
        }
        acceptIntent(intent)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); acceptIntent(intent) }
    private fun acceptIntent(i: Intent?) {
        if (i?.action == Intent.ACTION_VIEW) i.data?.let { importUri(it); i.action = Intent.ACTION_MAIN }
    }
    override fun onResume() { super.onResume(); if (::manager.isInitialized && !busy) render() }
    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); out.putString("page",page); out.putString("detail",detailId); out.putString("preview",previewKind); out.putBoolean("role",pendingRole) }
    override fun onDestroy() { progressDialog?.dismiss(); worker.shutdown(); super.onDestroy() }
    @Deprecated("Supported on Android 10 and later")
    override fun onBackPressed() { if (busy) return; if (detailId != null || page != "library") { detailId = null; page = "library"; render() } else super.onBackPressed() }

    private fun render() {
        if (isFinishing || isDestroyed) return
        root = Ui.column(this).apply { setBackgroundColor(Ui.bg) }
        Ui.setup(this,root)
        val content = Ui.column(this).apply { Ui.padded(this,22) }
        val scroll = ScrollView(this).apply { isFillViewport = false; clipToPadding = false; addView(content) }
        Ui.add(root,scroll,0,1f)
        val detail = detailId?.let { manager.themeById(it) }
        if (detail != null) detail(content,detail) else {
            detailId = null
            when(page) { "settings" -> settings(content); else -> library(content) }
        }
        if (detail != null) footerAction("一键应用", { apply(detail) })
        else if (page == "library") footerAction("＋ 导入主题包", { pickTheme() })
        nav()
        if (busy) root.isEnabled = false
    }
    private fun header(parent: LinearLayout, eyebrow: String, title: String, subtitle: String) {
        Ui.add(parent,Ui.text(this,eyebrow,12f,Ui.muted,true)); Ui.add(parent,Ui.space(this,8))
        Ui.add(parent,Ui.text(this,title,32f,Ui.ink,true)); Ui.add(parent,Ui.space(this,8))
        Ui.add(parent,Ui.text(this,subtitle,14f,Ui.muted)); Ui.add(parent,Ui.space(this,24))
    }
    private fun library(parent: LinearLayout) {
        val themes = manager.listThemes(); val active = manager.currentTheme()?.id
        header(parent,"SHIGUANG  /  拾光主题", "我的主题", "收藏一份喜欢，让每次点亮都有好心情。")
        val summary = Ui.row(this)
        summary.addView(Ui.text(this,"${themes.size} 套主题",15f,Ui.ink,true),LinearLayout.LayoutParams(0,-2,1f))
        summary.addView(Ui.text(this,Ui.mb(manager.storageBytes()),13f,Ui.muted)); Ui.add(parent,summary); Ui.add(parent,Ui.space(this,16))
        if (themes.isEmpty()) {
            val empty = Ui.card(this); Ui.padded(empty,24)
            Ui.add(empty,Ui.text(this,"你的下一份喜欢",25f,Ui.ink,true)); Ui.add(empty,Ui.space(this,12))
            Ui.add(empty,Ui.text(this,"导入 .ltheme 主题包，锁屏、桌面和图标会一起收藏在这里。",16f,Ui.muted))
            Ui.add(empty,Ui.space(this,24)); Ui.add(empty,Ui.button(this,"导入第一套主题",false) { pickTheme() }); Ui.add(parent,empty)
        }
        themes.forEach { theme ->
            val card = Ui.card(this)
            val image = image(theme.previewFile,ImageView.ScaleType.CENTER_CROP,"${theme.name}主题封面")
            Ui.add(card,image,230)
            val info = Ui.column(this); Ui.padded(info,20)
            val titleRow = Ui.row(this)
            titleRow.addView(Ui.text(this,theme.name,23f,Ui.ink,true),LinearLayout.LayoutParams(0,-2,1f))
            if (active == theme.id) titleRow.addView(Ui.text(this,"已选用",12f,Ui.green,true).apply { background=Ui.shape(Ui.soft,Ui.dp(context,12)); Ui.padded(this,8) })
            Ui.add(info,titleRow); Ui.add(info,Ui.space(this,6)); Ui.add(info,Ui.text(this,"${theme.author}  ·  V${theme.version}",13f,Ui.muted))
            Ui.add(info,Ui.space(this,12)); Ui.add(info,Ui.text(this,theme.description,15f,Ui.muted))
            Ui.add(info,Ui.space(this,16)); Ui.add(info,Ui.button(this,"查看主题") { detailId=theme.id; previewKind="lock"; render() })
            Ui.add(card,info); Ui.add(parent,card); Ui.add(parent,Ui.space(this,18))
        }
        Ui.add(parent,Ui.text(this,"主题永久保存在本机，更新同一主题不会重复入库。",12f,Ui.muted))
    }
    private fun detail(parent: LinearLayout, theme: ThemePackage) {
        val top = Ui.row(this)
        top.addView(Ui.button(this,"‹ 返回") { detailId=null; render() },LinearLayout.LayoutParams(0,-2,1f))
        val gap = View(this); top.addView(gap,LinearLayout.LayoutParams(Ui.dp(this,12),1))
        top.addView(Ui.button(this,"删除") { delete(theme) },LinearLayout.LayoutParams(0,-2,1f)); Ui.add(parent,top); Ui.add(parent,Ui.space(this,24))
        header(parent,"THEME COLLECTION",theme.name,"${theme.author}  ·  V${theme.version}  ·  ${Ui.mb(theme.bytes)}")
        val tabs = Ui.row(this)
        listOf("lock" to "锁屏", "home" to "桌面", "icons" to "图标").forEach { (kind,label) ->
            val b=Ui.button(this,label,previewKind==kind) { previewKind=kind; render() }
            val lp=LinearLayout.LayoutParams(0,Ui.dp(this,52),1f).apply { marginEnd=Ui.dp(this@MainActivity,5) }; tabs.addView(b,lp)
        }; Ui.add(parent,tabs); Ui.add(parent,Ui.space(this,16))
        val file = when(previewKind) { "home" -> theme.homePreview; "icons" -> theme.iconsPreview; else -> theme.lockPreview }
        val preview = image(file,ImageView.ScaleType.FIT_CENTER,"${theme.name}${when(previewKind){"home"->"桌面";"icons"->"图标";else->"锁屏"}}效果预览")
        preview.setBackgroundColor(Ui.soft); preview.background = Ui.shape(Ui.soft,Ui.dp(this,22)); preview.clipToOutline=true
        Ui.add(parent,preview,if(previewKind=="icons") 300 else 380)
        Ui.add(parent,Ui.space(this,10)); Ui.add(parent,Ui.text(this,"预览供搭配参考，系统时钟与实际应用排列以手机为准。",12f,Ui.muted))
        Ui.add(parent,Ui.space(this,22)); Ui.add(parent,Ui.text(this,theme.description,16f,Ui.muted))
        Ui.add(parent,Ui.space(this,22)); Ui.add(parent,Ui.text(this,"这一套包含",19f,Ui.ink,true)); Ui.add(parent,Ui.space(this,10))
        Ui.add(parent,Ui.text(this,"锁屏壁纸 · 桌面壁纸 · ${theme.icons.size} 个应用图标映射\n${theme.categories.size} 类通用图标 · 自动适配未收录的应用",14f,Ui.muted))
        Ui.add(parent,Ui.space(this,20)); compatibility(parent,theme)
    }
    private fun compatibility(parent: LinearLayout, theme: ThemePackage? = null) {
        val wm=WallpaperManager.getInstance(this)
        val box=Ui.card(this); Ui.padded(box,18)
        Ui.add(box,Ui.text(this,"设备兼容性",18f,Ui.ink,true)); Ui.add(box,Ui.space(this,10))
        val required=theme?.minAndroid ?: 10
        val compatible=Build.VERSION.SDK_INT >= required+19
        Ui.add(box,Ui.text(this,"${Build.MANUFACTURER}  ·  Android ${Build.VERSION.RELEASE}\n"+
            (if(compatible) "符合 Android $required+ 要求" else "此主题要求 Android $required 或更高版本")+"\n"+
            (if(wm.isWallpaperSupported && wm.isSetWallpaperAllowed) "壁纸更换：系统允许" else "壁纸更换：系统有限制")+"\n"+
            (if(isHome()) "主题图标：拾光桌面已启用" else "主题图标：应用时确认默认桌面即可启用"),14f,Ui.muted))
        Ui.add(box,Ui.space(this,12)); Ui.add(box,Ui.text(this,"部分手机会限制锁屏壁纸。遇到限制时会明确提示，其他可用功能继续生效。",12f,Ui.muted)); Ui.add(parent,box)
    }
    private fun settings(parent: LinearLayout) {
        header(parent,"A LITTLE ABOUT SHIGUANG","我的拾光","简单、安静，只把喜欢留在身边。")
        compatibility(parent); Ui.add(parent,Ui.space(this,18))
        Ui.add(parent,Ui.button(this,"设置默认桌面") { requestHome() }); Ui.add(parent,Ui.space(this,12))
        Ui.add(parent,Ui.button(this,"恢复系统默认") { restore() }); Ui.add(parent,Ui.space(this,12))
        Ui.add(parent,Ui.button(this,"切回其他桌面") { openHomeSettings() }); Ui.add(parent,Ui.space(this,22))
        val card=Ui.card(this); Ui.padded(card,20)
        Ui.add(card,Ui.text(this,"本机收藏",18f,Ui.ink,true)); Ui.add(card,Ui.space(this,12))
        Ui.add(card,Ui.text(this,"主题占用  ${Ui.mb(manager.storageBytes())}\n已收藏  ${manager.listThemes().size} 套\n拾光主题  V1.0.0",14f,Ui.muted)); Ui.add(parent,card)
        Ui.add(parent,Ui.space(this,22)); Ui.add(parent,Ui.text(this,"你的主题只保存在这台设备。无需登录，不上传图片或应用列表。卸载软件会移除收藏，请保留原始主题包。\n\n主题用于壁纸与拾光桌面图标；系统通知、控制中心与锁屏时钟由手机系统管理。",13f,Ui.muted))
    }
    private fun footerAction(label: String, action: () -> Unit) {
        val box=Ui.column(this); box.setPadding(Ui.dp(this,22),Ui.dp(this,8),Ui.dp(this,22),Ui.dp(this,8))
        Ui.add(box,Ui.button(this,label,true) { if(!busy) action() }); Ui.add(root,box)
    }
    private fun nav() {
        val bar=Ui.row(this); bar.setPadding(Ui.dp(this,16),Ui.dp(this,4),Ui.dp(this,16),Ui.dp(this,8))
        listOf("library" to "主题库", "launcher" to "桌面", "settings" to "我的").forEach { (key,label) ->
            val b=Ui.button(this,label,page==key && detailId==null) {
                if(!busy) if(key=="launcher") startActivity(Intent(this,LauncherActivity::class.java)) else { page=key; detailId=null; render() }
            }
            bar.addView(b,LinearLayout.LayoutParams(0,Ui.dp(this,48),1f).apply { marginEnd=Ui.dp(this@MainActivity,4) })
        }; Ui.add(root,bar)
    }
    private fun image(file: File?, scale: ImageView.ScaleType, label: String) = ImageView(this).apply {
        scaleType=scale; contentDescription=label; adjustViewBounds=true
        file?.let { setImageBitmap(ThemeManager.decodeSampled(it,1080)) }
    }
    private fun pickTheme() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="*/*" },1001)
    }
    @Deprecated("Supported on Android 10 and later")
    override fun onActivityResult(requestCode: Int,resultCode: Int,data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==1001 && resultCode==RESULT_OK) data?.data?.let { importUri(it) }
        if(requestCode==1002 && pendingRole) {
            pendingRole=false
            if(isHome()) toast("主题图标已启用，按主屏幕键即可查看")
            else AlertDialog.Builder(this).setTitle("壁纸结果已保留").setMessage("你暂未选择拾光桌面。主题图标可在下方“桌面”中预览，稍后也能在“我的”中设为默认桌面。")
                .setPositiveButton("知道了",null).show()
        }
    }
    private fun importUri(uri: Uri) {
        if(busy) return
        background("正在收藏主题…",{manager.importTheme(uri)}) { result ->
            page="library"; detailId=result.theme.id; previewKind="lock"; render()
            toast(if(result.updated) "主题已更新，点击一键应用更新壁纸" else "${result.theme.name}已收藏到主题库")
        }
    }
    private fun apply(theme: ThemePackage) {
        if(Build.VERSION.SDK_INT < theme.minAndroid+19) { toast("此主题需要 Android ${theme.minAndroid} 或更高版本"); return }
        background("正在应用主题…",{manager.applyTheme(theme)}) { result ->
            render()
            val message=result.messages.joinToString("\n")
            if(result.wallpaperCount<2) AlertDialog.Builder(this).setTitle("应用结果").setMessage(message).setPositiveButton(if(isHome()) "查看桌面" else "启用主题图标") { _,_ -> finishApply() }.setNegativeButton("稍后",null).show()
            else { toast(message); finishApply() }
        }
    }
    private fun finishApply() { if(isHome()) startActivity(Intent(this,LauncherActivity::class.java)) else { pendingRole=true; requestHome() } }
    private fun delete(theme: ThemePackage) {
        if(manager.currentTheme()?.id==theme.id) {
            AlertDialog.Builder(this).setTitle("这套主题正在使用").setMessage("请先应用另一套主题，或在“我的”中恢复系统默认，再删除这一套。")
                .setPositiveButton("知道了",null).show(); return
        }
        AlertDialog.Builder(this).setTitle("删除“${theme.name}”？").setMessage("会移除本机收藏。你仍可以从原始主题包重新导入。")
            .setNegativeButton("保留",null).setPositiveButton("删除") { _,_ -> background("正在移除主题…",{manager.deleteTheme(theme.id)}) { ok ->
                if(ok) { detailId=null; render(); toast("主题已删除") } else toast("删除未完成，请稍后重试")
            } }.show()
    }
    private fun restore() {
        AlertDialog.Builder(this).setTitle("恢复系统默认？").setMessage("桌面和锁屏壁纸会重置为系统默认，拾光桌面会使用应用原始图标。收藏的主题会保留。\n\n这不会找回应用主题之前的自选壁纸。")
            .setNegativeButton("取消",null).setPositiveButton("恢复") { _,_ -> background("正在恢复…",{manager.restoreDefault()}) { messages ->
                render(); AlertDialog.Builder(this).setTitle("恢复结果").setMessage(messages.joinToString("\n")).setPositiveButton("完成",null).show()
            } }.show()
    }
    private fun isHome(): Boolean = try { getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME) } catch(_:Exception){false}
    private fun requestHome() {
        try {
            val rm=getSystemService(RoleManager::class.java)
            if(isHome()) { toast("拾光已是默认桌面"); if(pendingRole){pendingRole=false; startActivity(Intent(this,LauncherActivity::class.java))} }
            else if(rm.isRoleAvailable(RoleManager.ROLE_HOME)) startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME),1002) else openHomeSettings()
        } catch(_:Exception) { openHomeSettings() }
    }
    private fun openHomeSettings() { try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } catch(_:Exception){ toast("请在手机设置中搜索“默认桌面”") } }
    private fun toast(s:String) { Toast.makeText(this,s,Toast.LENGTH_LONG).show() }
    private fun <T> background(label:String, task:()->T, done:(T)->Unit) {
        if(busy) return; busy=true
        val dialog=AlertDialog.Builder(this).setMessage(label).setCancelable(false).create(); progressDialog=dialog; dialog.show()
        worker.execute {
            try { val result=task(); runOnUiThread { busy=false; if(!isFinishing && !isDestroyed){dialog.dismiss(); done(result)} } }
            catch(e:Exception) { runOnUiThread { busy=false; if(!isFinishing && !isDestroyed){dialog.dismiss(); AlertDialog.Builder(this).setTitle("暂未完成").setMessage(if(e is ThemeException) e.message else "暂时无法完成，请稍后再试。").setPositiveButton("知道了",null).show()} } }
        }
    }
}

package com.example.localthemeloader

import android.app.Activity
import android.content.*
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.text.*
import android.view.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class LauncherActivity : Activity() {
    private lateinit var manager:ThemeManager
    private var apps=emptyList<ResolveInfo>()
    private var adapter:AppGridAdapter?=null
    private val receiver=object:BroadcastReceiver(){override fun onReceive(c:Context?,i:Intent?){refresh()}}
    override fun onCreate(state:Bundle?) {
        super.onCreate(state); manager=ThemeManager(applicationContext)
        val filter=IntentFilter().apply { addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package") }
        if(android.os.Build.VERSION.SDK_INT>=33) registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED) else registerReceiver(receiver,filter)
        refresh()
    }
    override fun onResume(){super.onResume(); if(::manager.isInitialized) refresh()}
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent); refresh()}
    override fun onDestroy(){unregisterReceiver(receiver); super.onDestroy()}
    @Deprecated("Home never exits on back")
    override fun onBackPressed(){refresh()}
    private fun refresh(){
        if(isDestroyed || isFinishing) return
        val theme=manager.currentTheme()
        apps=packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
            .filter { it.activityInfo.packageName!=packageName }
            .distinctBy { it.activityInfo.packageName+"/"+it.activityInfo.name }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase(Locale.getDefault()) }
        val root=android.widget.FrameLayout(this)
        val wallpaper=ImageView(this).apply { scaleType=ImageView.ScaleType.CENTER_CROP; theme?.homeWallpaper?.let{setImageBitmap(ThemeManager.decodeSampled(it,1440))}; setBackgroundColor(Ui.bg); importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        root.addView(wallpaper,android.widget.FrameLayout.LayoutParams(-1,-1))
        val content=Ui.column(this); content.setPadding(Ui.dp(this,22),Ui.dp(this,12),Ui.dp(this,22),Ui.dp(this,12))
        root.addView(content,android.widget.FrameLayout.LayoutParams(-1,-1)); Ui.setup(this,root)
        val clock=Ui.card(this); clock.background=Ui.shape(0xEAF7F6F0.toInt(),Ui.dp(this,22)); Ui.padded(clock,16)
        Ui.add(clock,TextClock(this).apply { format12Hour="HH:mm"; format24Hour="HH:mm"; textSize=48f; setTextColor(Ui.ink); typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL) })
        Ui.add(clock,TextClock(this).apply { format12Hour="M月d日  EEEE"; format24Hour="M月d日  EEEE"; textSize=14f; setTextColor(Ui.muted) })
        Ui.add(content,clock); Ui.add(content,Ui.space(this,14))
        val search=EditText(this).apply { hint="搜索应用"; textSize=16f; setTextColor(Ui.ink); setHintTextColor(Ui.muted); isSingleLine=true; background=Ui.shape(0xEEF7F6F0.toInt(),Ui.dp(this@LauncherActivity,18)); Ui.padded(this,14); inputType=android.text.InputType.TYPE_CLASS_TEXT; imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_DONE; contentDescription="搜索应用" }
        Ui.add(content,search,52); Ui.add(content,Ui.space(this,14))
        val panel=Ui.card(this); panel.background=Ui.shape(0xEAF7F6F0.toInt(),Ui.dp(this,24)); panel.setPadding(Ui.dp(this,8),Ui.dp(this,12),Ui.dp(this,8),Ui.dp(this,4))
        val title=Ui.text(this,"所有应用  ·  ${apps.size}",13f,Ui.muted,true).apply { setPadding(Ui.dp(context,12),0,0,Ui.dp(context,8)) }; Ui.add(panel,title)
        val grid=GridView(this).apply { numColumns=if(resources.configuration.screenWidthDp>=600) 6 else 4; verticalSpacing=Ui.dp(this@LauncherActivity,4); stretchMode=GridView.STRETCH_COLUMN_WIDTH; clipToPadding=false; setSelector(android.graphics.drawable.ColorDrawable(0x22315741)) }
        val newAdapter=AppGridAdapter(this,apps,theme); adapter=newAdapter; grid.adapter=newAdapter
        grid.setOnItemClickListener { _,_,p,_ -> launch(newAdapter.getItem(p)) }
        Ui.add(panel,grid,0,1f); Ui.add(content,panel,0,1f); Ui.add(content,Ui.space(this,12))
        val actions=Ui.row(this)
        actions.addView(Ui.button(this,"我的主题") { startActivity(Intent(this,MainActivity::class.java)) },LinearLayout.LayoutParams(0,Ui.dp(this,52),1f).apply{marginEnd=Ui.dp(this@LauncherActivity,8)})
        actions.addView(Ui.button(this,"桌面设置") { startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS)) },LinearLayout.LayoutParams(0,Ui.dp(this,52),1f)); Ui.add(content,actions)
        search.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){ val q=s.toString().trim(); val found=apps.filter{it.loadLabel(packageManager).toString().contains(q,true)}; newAdapter.update(found,theme); title.text=if(found.isEmpty()) "没有找到应用" else "所有应用  ·  ${found.size}" }
            override fun afterTextChanged(s:Editable?){}
        })
    }
    private fun launch(info:ResolveInfo){
        try { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(info.activityInfo.packageName,info.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)) }
        catch(_:Exception){Toast.makeText(this,"这个应用暂时无法打开，请稍后再试。",Toast.LENGTH_LONG).show()}
    }
}

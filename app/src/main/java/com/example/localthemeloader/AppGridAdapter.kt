package com.example.localthemeloader

import android.content.Context
import android.content.pm.ResolveInfo
import android.graphics.*
import android.graphics.drawable.*
import android.view.*
import android.widget.*

class AppGridAdapter(private val context: Context, private var apps: List<ResolveInfo>, private var theme: ThemePackage?) : BaseAdapter() {
    private val cache = HashMap<String,Drawable>()
    fun update(newApps: List<ResolveInfo>, newTheme: ThemePackage?) { apps=newApps; theme=newTheme; cache.clear(); notifyDataSetChanged() }
    override fun getCount()=apps.size
    override fun getItem(position:Int)=apps[position]
    override fun getItemId(position:Int)=position.toLong()
    override fun getView(position:Int,convertView:View?,parent:ViewGroup?):View {
        val cell=convertView as? LinearLayout ?: Ui.column(context).apply {
            gravity=Gravity.CENTER; setPadding(Ui.dp(context,4),Ui.dp(context,10),Ui.dp(context,4),Ui.dp(context,8))
            addView(ImageView(context).apply { id=R.id.app_icon; scaleType=ImageView.ScaleType.FIT_CENTER },LinearLayout.LayoutParams(Ui.dp(context,54),Ui.dp(context,54)))
            addView(Ui.text(context,"",12f).apply { id=R.id.app_label; gravity=Gravity.CENTER; maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END },LinearLayout.LayoutParams(-1,Ui.dp(context,40)))
        }
        val info=getItem(position); val label=info.loadLabel(context.packageManager).toString()
        cell.findViewById<ImageView>(R.id.app_icon).setImageDrawable(icon(info)); cell.findViewById<ImageView>(R.id.app_icon).contentDescription=null
        cell.findViewById<TextView>(R.id.app_label).text=label; cell.findViewById<TextView>(R.id.app_label).setTextColor(Ui.ink)
        cell.contentDescription="打开$label"; cell.minimumHeight=Ui.dp(context,100)
        return cell
    }
    private fun icon(info:ResolveInfo):Drawable {
        val pkg=info.activityInfo.packageName; val key=pkg+"/"+info.activityInfo.name
        return cache.getOrPut(key) {
            val file=theme?.icons?.get(key) ?: theme?.icons?.get(pkg)
            val bitmap=file?.let { ThemeManager.decodeSampled(it,180) }
            if(bitmap!=null) BitmapDrawable(context.resources,bitmap) else {
                val original=info.loadIcon(context.packageManager)
                if(theme==null) original else {
                    val canvasBitmap=Bitmap.createBitmap(180,180,Bitmap.Config.ARGB_8888); val canvas=Canvas(canvasBitmap)
                    val plate=theme?.categories?.get("default")?.let { ThemeManager.decodeSampled(it,180) }
                    if(plate!=null){canvas.drawBitmap(plate,null,Rect(0,0,180,180),Paint(Paint.ANTI_ALIAS_FLAG))}
                    else canvas.drawRoundRect(RectF(3f,3f,177f,177f),42f,42f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Ui.soft })
                    original.setBounds(38,38,142,142); original.draw(canvas); BitmapDrawable(context.resources,canvasBitmap)
                }
            }
        }
    }
}

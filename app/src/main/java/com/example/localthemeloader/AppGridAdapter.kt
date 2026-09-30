package com.example.localthemeloader

import android.content.Context
import android.content.pm.ResolveInfo
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

class AppGridAdapter(
    private val context: Context,
    private var apps: List<ResolveInfo>,
    private var theme: ThemePackage?
) : BaseAdapter() {

    fun update(newApps: List<ResolveInfo>, newTheme: ThemePackage?) {
        apps = newApps
        theme = newTheme
        notifyDataSetChanged()
    }

    override fun getCount(): Int = apps.size
    override fun getItem(position: Int): ResolveInfo = apps[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.app_item, parent, false)
        val info = getItem(position)
        val iconView = view.findViewById<ImageView>(R.id.icon)
        val labelView = view.findViewById<TextView>(R.id.label)
        val pkg = info.activityInfo.packageName

        labelView.text = info.loadLabel(context.packageManager)
        iconView.setImageDrawable(themedIcon(pkg) ?: info.loadIcon(context.packageManager))
        return view
    }

    private fun themedIcon(packageName: String): Drawable? {
        val file = theme?.icons?.get(packageName) ?: return null
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        return BitmapDrawable(context.resources, bmp)
    }
}

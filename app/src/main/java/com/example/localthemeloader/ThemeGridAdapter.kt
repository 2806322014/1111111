package com.example.localthemeloader

import android.content.Context
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

class ThemeGridAdapter(
    private val context: Context,
    private var themes: List<ThemePackage>,
    private var selectedId: String?
) : BaseAdapter() {

    fun update(newThemes: List<ThemePackage>, newSelectedId: String?) {
        themes = newThemes
        selectedId = newSelectedId
        notifyDataSetChanged()
    }

    override fun getCount(): Int = themes.size
    override fun getItem(position: Int): ThemePackage = themes[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.theme_item, parent, false)
        val theme = getItem(position)
        val image = view.findViewById<ImageView>(R.id.themePreview)
        val name = view.findViewById<TextView>(R.id.themeName)
        val author = view.findViewById<TextView>(R.id.themeAuthor)

        name.text = if (theme.id == selectedId) "✓ " + theme.name else theme.name
        author.text = if (theme.author.isBlank()) "本地主题" else theme.author

        val preview = theme.previewFile
        if (preview != null && preview.exists()) {
            image.setImageBitmap(BitmapFactory.decodeFile(preview.absolutePath))
        } else {
            image.setImageDrawable(null)
        }
        return view
    }
}

package com.example.localthemeloader

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.*

object Ui {
    val bg = Color.rgb(247, 249, 255)
    val ink = Color.rgb(19, 24, 43)
    val muted = Color.rgb(102, 112, 133)
    val green = Color.rgb(69, 95, 243)
    val soft = Color.rgb(237, 242, 255)
    val border = Color.rgb(218, 226, 246)
    val purple = Color.rgb(129, 72, 244)
    fun dp(context: Context, n: Int) = (context.resources.displayMetrics.density * n).toInt()
    fun shape(color: Int, radius: Int = 20): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }
    fun gradient(c: Context, radius: Int = 22, subtle: Boolean = false) = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
        if (subtle) intArrayOf(Color.rgb(230, 243, 255), Color.rgb(240, 234, 255)) else intArrayOf(Color.rgb(33, 116, 212), Color.rgb(79, 96, 233), purple)).apply { cornerRadius = dp(c, radius).toFloat() }
    fun outline(c: Context, selected: Boolean = false, radius: Int = 18) = shape(Color.WHITE, dp(c, radius)).apply {
        setStroke(dp(c, if (selected) 2 else 1), if (selected) green else border)
    }
    fun icon(c: Context, resource: Int, color: Int = ink, size: Int = 24) = ImageView(c).apply {
        setImageResource(resource); imageTintList = ColorStateList.valueOf(color)
        layoutParams = LinearLayout.LayoutParams(dp(c, size), dp(c, size)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    fun iconButton(c: Context, resource: Int, label: String, action: () -> Unit) = ImageButton(c).apply {
        setImageResource(resource); imageTintList = ColorStateList.valueOf(ink); contentDescription = label
        setPadding(dp(c, 10), dp(c, 10), dp(c, 10), dp(c, 10))
        background = RippleDrawable(ColorStateList.valueOf(0x16455FF3), shape(Color.TRANSPARENT, dp(c, 18)), null)
        setOnClickListener { action() }
    }
    fun column(c: Context) = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
    fun row(c: Context) = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
    fun text(c: Context, value: String, size: Float = 16f, color: Int = ink, bold: Boolean = false) = TextView(c).apply {
        text = value; textSize = size; setTextColor(color); includeFontPadding = false
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setLineSpacing(dp(c, 3).toFloat(), 1.08f)
    }
    fun button(c: Context, label: String, primary: Boolean = false, action: () -> Unit) = Button(c).apply {
        text = label; textSize = 16f; isAllCaps = false; setTextColor(if (primary) Color.WHITE else ink)
        minHeight = dp(c, 52); minimumHeight = dp(c, 52); stateListAnimator = null
        background = RippleDrawable(ColorStateList.valueOf(0x22000000), if (primary) gradient(c, 22) else shape(soft, dp(c, 16)), null)
        setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8)); setOnClickListener { action() }
    }
    fun padded(v: View, amount: Int) { val d = dp(v.context, amount); v.setPadding(d,d,d,d) }
    fun space(c: Context, h: Int) = Space(c).apply { minimumHeight = dp(c,h); layoutParams = LinearLayout.LayoutParams(1, dp(c,h)) }
    fun add(parent: LinearLayout, child: View, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, weight: Float = 0f) {
        parent.addView(child, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, if (h < 0) h else dp(parent.context,h), weight))
    }
    fun card(c: Context) = column(c).apply { background = shape(Color.WHITE,dp(c,24)); clipToOutline = true; elevation = dp(c,1).toFloat() }
    fun setup(activity: Activity, root: View, dark: Boolean = false) {
        activity.window.statusBarColor = Color.TRANSPARENT
        activity.window.navigationBarColor = if (dark) Color.TRANSPARENT else bg
        activity.window.decorView.systemUiVisibility = if (dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = if (android.os.Build.VERSION.SDK_INT >= 30) insets.getInsets(android.view.WindowInsets.Type.systemBars()) else null
            view.setPadding(bars?.left ?: insets.systemWindowInsetLeft, bars?.top ?: insets.systemWindowInsetTop,
                bars?.right ?: insets.systemWindowInsetRight, bars?.bottom ?: insets.systemWindowInsetBottom)
            insets
        }
        activity.setContentView(root); root.requestApplyInsets()
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            activity.window.setDecorFitsSystemWindows(false)
            root.post {
                val mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                activity.window.insetsController?.setSystemBarsAppearance(if (dark) 0 else mask, mask)
            }
        }
    }
    fun mb(bytes: Long) = String.format(java.util.Locale.CHINA, "%.1f MB", bytes / 1048576.0)
}

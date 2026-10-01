package com.example.localthemeloader

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.graphics.*
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.*

object WidgetRenderer {
    val providers = listOf(ClockWidget::class.java, ClockWideWidget::class.java, DateWidget::class.java, DateWideWidget::class.java,
        PhotoWidget::class.java, PhotoWideWidget::class.java, QuoteWidget::class.java, QuoteWideWidget::class.java)
    fun kind(name: String) = name.substringAfterLast('.').removeSuffix("Widget").removeSuffix("Wide").lowercase(Locale.ROOT)
    fun component(context: Context, kind: String, wide: Boolean): ComponentName = ComponentName(context,
        providers.first { kind(it.simpleName) == kind && it.simpleName.contains("Wide") == wide })
    fun background(canvas: Canvas, rect: RectF, config: WidgetConfig) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = config.background }
        val radius = config.cornerRadius * (rect.width() / 180f).coerceAtMost(2f)
        canvas.save(); val clip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }; canvas.clipPath(clip)
        canvas.drawRect(rect, paint)
        val source = if (config.type == "photo") config.photo else config.backgroundImage
        ThemeFiles.decode(source, 600)?.let { b ->
            val scale = maxOf(rect.width() / b.width, rect.height() / b.height)
            val w = b.width * scale; val h = b.height * scale
            val dest = RectF(rect.centerX() - w / 2, rect.centerY() - h / 2, rect.centerX() + w / 2, rect.centerY() + h / 2)
            canvas.drawBitmap(b, null, dest, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)); b.recycle()
        }
        canvas.restore()
    }
    fun drawCard(canvas: Canvas, rect: RectF, config: WidgetConfig) {
        background(canvas, rect, config)
        if (config.type == "photo") return
        val text = when (config.type) {
            "clock" -> SimpleDateFormat(config.timeFormat, Locale.CHINA).format(Date())
            "date" -> SimpleDateFormat(config.dateFormat, Locale.CHINA).format(Date())
            else -> config.text
        }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = config.textColor; textSize = config.textSize * 1.3f }
        val width = (rect.width() - 40).toInt()
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width).setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(4).build()
        canvas.save(); canvas.clipRect(rect); canvas.translate(rect.left + 20, rect.centerY() - layout.height / 2f); layout.draw(canvas); canvas.restore()
    }
    fun views(context: Context, id: Int, provider: ComponentName, previewTheme: ThemePackage? = null): RemoteViews {
        val kind = kind(provider.className); val theme = previewTheme ?: WidgetConfigurationRepository(context).theme(id)
        val config = theme?.widgets?.get(kind) ?: WidgetConfigParser.defaults(kind)
        val wide = provider.className.contains("Wide")
        val rv = RemoteViews(context.packageName, R.layout.theme_widget)
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, if (wide) 250 else 110).coerceAtLeast(40)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110).coerceAtLeast(40)
        val ratio = (width.toFloat() / height).coerceIn(0.5f, 4f)
        val bitmap = Bitmap.createBitmap(if (ratio >= 1) 360 else (360 * ratio).toInt(), if (ratio >= 1) (360 / ratio).toInt() else 360, Bitmap.Config.ARGB_8888)
        background(Canvas(bitmap), RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()), config)
        rv.setImageViewBitmap(R.id.widget_background, bitmap)
        listOf(R.id.widget_time, R.id.widget_date, R.id.widget_quote).forEach { rv.setViewVisibility(it, View.GONE) }
        fun clock(view: Int, format: String, size: Float) {
            rv.setViewVisibility(view, View.VISIBLE); rv.setCharSequence(view, "setFormat12Hour", format); rv.setCharSequence(view, "setFormat24Hour", format)
            rv.setTextColor(view, config.textColor); rv.setTextViewTextSize(view, TypedValue.COMPLEX_UNIT_SP, size)
        }
        when (kind) {
            "clock" -> { clock(R.id.widget_time, config.timeFormat, config.textSize.coerceAtMost(if (wide) 56f else 38f)); if (config.showDate) clock(R.id.widget_date, config.dateFormat, 13f) }
            "date" -> clock(R.id.widget_date, config.dateFormat, config.textSize.coerceAtMost(28f))
            "quote" -> { rv.setViewVisibility(R.id.widget_quote, View.VISIBLE); rv.setTextViewText(R.id.widget_quote, config.text)
                rv.setTextColor(R.id.widget_quote, config.textColor); rv.setTextViewTextSize(R.id.widget_quote, TypedValue.COMPLEX_UNIT_SP, config.textSize.coerceAtMost(32f)) }
        }
        rv.setContentDescription(R.id.widget_root, "${WidgetConfigParser.kinds[kind]}组件 · ${theme?.name ?: "拾光主题"}")
        val i = if (theme != null) Intent(context, ThemeDetailActivity::class.java).putExtra("theme_id", theme.id) else Intent(context, MainActivity::class.java)
        rv.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, id, i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        return rv
    }
    fun refresh(context: Context, id: Int) {
        val manager = AppWidgetManager.getInstance(context); val provider = manager.getAppWidgetInfo(id)?.provider ?: return
        try { manager.updateAppWidget(id, views(context, id, provider)) } catch (_: Exception) { /* A removed host is harmless. */ }
    }
    fun refreshAll(context: Context) {
        providers.forEach { p -> AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, p)).forEach { refresh(context, it) } }
    }
}
open class ThemeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) { ids.forEach { WidgetRenderer.refresh(c, it) } }
    override fun onAppWidgetOptionsChanged(c: Context, m: AppWidgetManager, id: Int, options: Bundle) { WidgetRenderer.refresh(c, id) }
    override fun onDeleted(c: Context, ids: IntArray) { ids.forEach { WidgetConfigurationRepository(c).remove(it) } }
    override fun onReceive(c: Context, i: Intent) {
        super.onReceive(c, i)
        if (i.action in setOf(Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED)) WidgetRenderer.refreshAll(c)
    }
}
class ClockWidget : ThemeWidgetProvider()
class ClockWideWidget : ThemeWidgetProvider()
class DateWidget : ThemeWidgetProvider()
class DateWideWidget : ThemeWidgetProvider()
class PhotoWidget : ThemeWidgetProvider()
class PhotoWideWidget : ThemeWidgetProvider()
class QuoteWidget : ThemeWidgetProvider()
class QuoteWideWidget : ThemeWidgetProvider()

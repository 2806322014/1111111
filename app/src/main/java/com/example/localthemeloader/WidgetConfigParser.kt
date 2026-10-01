package com.example.localthemeloader

import android.graphics.Color
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

data class WidgetConfig(val type: String, val background: Int, val backgroundImage: File?, val textColor: Int,
    val textSize: Float, val cornerRadius: Float, val timeFormat: String, val dateFormat: String,
    val showDate: Boolean, val text: String, val photo: File?)

object WidgetConfigParser {
    val kinds = linkedMapOf("clock" to "时钟", "date" to "日期", "photo" to "照片", "quote" to "文案")
    fun defaults(type: String) = WidgetConfig(type, Ui.soft, null, Ui.ink, if (type == "clock") 36f else 18f,
        24f, "HH:mm", "MM月dd日 EEEE", true, "把喜欢留在日常", null)
    fun parse(root: File, file: File, expected: String): WidgetConfig {
        if (!file.isFile || file.length() > 32 * 1024) throw ThemeException("组件配置缺失或过大。")
        try {
            val j = JSONObject(file.readText(Charsets.UTF_8)); val d = defaults(expected)
            if (j.getString("type") != expected || expected !in kinds) throw ThemeException("组件类型与主题信息不一致。")
            fun color(key: String, fallback: Int) = if (j.has(key)) Color.parseColor(j.getString(key)) else fallback
            fun number(key: String, fallback: Float, min: Float, max: Float): Float {
                if (!j.has(key)) return fallback
                val value = j.getDouble(key).toFloat()
                if (!value.isFinite() || value !in min..max) throw ThemeException("组件的大小或圆角设置超出了范围。")
                return value
            }
            fun image(key: String): File? {
                val p = j.optString(key, "")
                if (p.isBlank()) return null
                return ThemeFiles.safeFile(root, p).also { ThemeFiles.validateImage(it) }
            }
            fun format(key: String, fallback: String): String {
                val value = j.optString(key, fallback)
                if (value.isBlank() || value.length > 64) throw ThemeException("组件时间格式为空或过长。")
                SimpleDateFormat(value, Locale.CHINA).format(java.util.Date())
                return value
            }
            val text = j.optString("text", d.text)
            if (text.length > 500) throw ThemeException("组件文案过长，请控制在 500 字以内。")
            val photo = image("photo") ?: image("photo_image")
            if (expected == "photo" && photo == null) throw ThemeException("照片组件缺少照片图片。")
            return WidgetConfig(expected, color("background", d.background), image("background_image"), color("text_color", d.textColor),
                number("text_size", d.textSize, 10f, 64f), number("corner_radius", d.cornerRadius, 0f, 64f),
                format("time_format", d.timeFormat), format("date_format", d.dateFormat), j.optBoolean("show_date", true), text, photo)
        } catch (e: ThemeException) { throw e }
        catch (_: Exception) { throw ThemeException("组件配置无法读取，请联系主题作者检查。") }
    }
}

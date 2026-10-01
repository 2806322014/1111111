package com.example.localthemeloader

import android.app.PendingIntent
import android.content.*
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import java.security.MessageDigest
import java.util.UUID

data class LaunchableApp(val component: ComponentName, val name: String, val original: Drawable)
data class ShortcutRequest(val accepted: Boolean, val message: String, val token: String = "")
interface ShortcutPinBackend { fun supported(): Boolean; fun request(info: ShortcutInfo, callback: IntentSender): Boolean }
class SystemShortcutPinBackend(context: Context) : ShortcutPinBackend {
    private val manager = context.getSystemService(ShortcutManager::class.java)
    override fun supported() = manager?.isRequestPinShortcutSupported == true
    override fun request(info: ShortcutInfo, callback: IntentSender) = manager?.requestPinShortcut(info, callback) == true
}
class ShortcutIconManager(private val context: Context, private val backend: ShortcutPinBackend = SystemShortcutPinBackend(context)) {
    fun apps(): List<LaunchableApp> = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .filter { it.activityInfo.packageName != context.packageName }.distinctBy { it.activityInfo.packageName + "/" + it.activityInfo.name }
        .map { LaunchableApp(ComponentName(it.activityInfo.packageName, it.activityInfo.name), it.loadLabel(context.packageManager).toString(), it.loadIcon(context.packageManager)) }.sortedBy { it.name }
    fun bitmap(theme: ThemePackage, app: LaunchableApp): Bitmap {
        val source = theme.icons[app.component.flattenToString()] ?: theme.icons[app.component.flattenToShortString()] ?: theme.icons[app.component.packageName]
        val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888); val canvas = Canvas(bitmap)
        ThemeFiles.decode(source, 192)?.let { b -> canvas.drawBitmap(b, null, Rect(0, 0, 192, 192), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)); b.recycle(); return bitmap }
        val plate = theme.categories["default"] ?: theme.icons["default"]
        ThemeFiles.decode(plate, 192)?.let { b -> canvas.drawBitmap(b, null, Rect(0, 0, 192, 192), Paint(Paint.FILTER_BITMAP_FLAG)); b.recycle() }
            ?: canvas.drawRoundRect(0f, 0f, 192f, 192f, 46f, 46f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.accentColor })
        app.original.mutate().apply { setBounds(35, 35, 157, 157); draw(canvas) }
        return bitmap
    }
    fun request(theme: ThemePackage, app: LaunchableApp): ShortcutRequest {
        return try {
            if (!backend.supported()) return ShortcutRequest(false, UNSUPPORTED_MESSAGE)
            val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            if (context.packageManager.resolveActivity(launch, 0) == null) return ShortcutRequest(false, "这个应用已移除，无法添加图标。")
            val id = MessageDigest.getInstance("SHA-256").digest((theme.id + app.component.flattenToString()).toByteArray()).take(16).joinToString("") { "%02x".format(it) }
            val info = ShortcutInfo.Builder(context, "theme_$id").setShortLabel(app.name.take(40)).setLongLabel("${app.name} · ${theme.name}".take(80)).setIcon(Icon.createWithBitmap(bitmap(theme, app))).setIntent(launch).build()
            val token = UUID.randomUUID().toString()
            val intent = Intent(context, ShortcutPinnedReceiver::class.java).setData(Uri.parse("shiguang://shortcut/$token")).putExtra("token", token)
            val callback = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            if (backend.request(info, callback.intentSender)) { ThemeApplyStateRepository(context).markShortcut(theme); ShortcutRequest(true, "请在桌面的确认窗口中添加“${app.name}”。", token) }
            else ShortcutRequest(false, UNSUPPORTED_MESSAGE)
        } catch (_: Exception) { ShortcutRequest(false, "暂时无法添加这个图标，请稍后重试或在桌面手动添加应用。") }
    }
    fun confirmed(token: String) = context.getSharedPreferences("shortcut_callbacks", 0).getBoolean(token, false)
    fun consume(token: String) { context.getSharedPreferences("shortcut_callbacks", 0).edit().remove(token).apply() }
    companion object { const val UNSUPPORTED_MESSAGE = "当前桌面不支持添加主题图标快捷方式，请在手机桌面中手动添加应用。" }
}
class ShortcutPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { val token = intent.getStringExtra("token") ?: return
        if (token.length <= 50) context.getSharedPreferences("shortcut_callbacks", 0).edit().putBoolean(token, true).commit() }
}

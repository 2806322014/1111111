package com.example.localthemeloader

import android.app.*
import android.os.Bundle
import android.widget.*
import java.io.File
import java.util.concurrent.Executors

open class ThemeScreenActivity : Activity() {
    protected lateinit var repository: ThemeRepository
    protected var busy = false
    private val worker = Executors.newSingleThreadExecutor()
    private var dialog: AlertDialog? = null
    override fun onCreate(state: Bundle?) { super.onCreate(state); repository = ThemeRepository(applicationContext) }
    override fun onDestroy() { dialog?.dismiss(); worker.shutdown(); super.onDestroy() }
    protected fun frame(title: String, back: Boolean = true, footer: Pair<String, () -> Unit>? = null): LinearLayout {
        val root = Ui.column(this).apply { setBackgroundColor(Ui.bg) }; Ui.setup(this, root)
        val top = Ui.row(this).apply { setPadding(Ui.dp(context, 20), Ui.dp(context, 12), Ui.dp(context, 20), 0) }
        if (back) top.addView(Ui.button(this, "返回") { finish() }, LinearLayout.LayoutParams(Ui.dp(this, 80), Ui.dp(this, 48)).apply { marginEnd = Ui.dp(this@ThemeScreenActivity, 12) })
        top.addView(Ui.text(this, title, 20f, Ui.ink, true), LinearLayout.LayoutParams(0, -2, 1f)); Ui.add(root, top)
        val content = Ui.column(this).apply { Ui.padded(this, 22) }
        Ui.add(root, ScrollView(this).apply { addView(content); clipToPadding = false }, 0, 1f)
        footer?.let { (label, action) -> val box = Ui.column(this).apply { setPadding(Ui.dp(context, 22), Ui.dp(context, 8), Ui.dp(context, 22), Ui.dp(context, 12)) }
            Ui.add(box, Ui.button(this, label, true) { if (!busy) action() }); Ui.add(root, box) }
        return content
    }
    protected fun image(file: File?, label: String, height: Int, crop: Boolean = false, maxEdge: Int = 1080) = ImageView(this).apply {
        scaleType = if (crop) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
        contentDescription = label; background = Ui.shape(Ui.soft, Ui.dp(context, 22)); clipToOutline = true
        setImageBitmap(ThemeFiles.decode(file, maxEdge)); layoutParams = LinearLayout.LayoutParams(-1, Ui.dp(context, height))
    }
    protected fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    protected fun error(message: String) { AlertDialog.Builder(this).setTitle("暂未完成").setMessage(message).setPositiveButton("知道了", null).show() }
    protected fun <T> background(label: String, task: () -> T, done: (T) -> Unit) {
        if (busy) return
        busy = true; dialog = AlertDialog.Builder(this).setMessage(label).setCancelable(false).create().also { it.show() }
        worker.execute {
            try { val result = task(); runOnUiThread { busy = false; dialog?.dismiss()
                if (!isFinishing && !isDestroyed) done(result) else if (result is AutoCloseable) result.close() } }
            catch (e: Exception) { runOnUiThread { busy = false; dialog?.dismiss()
                if (!isFinishing && !isDestroyed) error(if (e is ThemeException) e.message ?: "暂时无法完成。" else "暂时无法完成，请稍后再试。") } }
        }
    }
    protected fun openDetail(id: String) { startActivity(android.content.Intent(this, ThemeDetailActivity::class.java).putExtra("theme_id", id)) }
}

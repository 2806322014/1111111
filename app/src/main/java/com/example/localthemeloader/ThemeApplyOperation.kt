package com.example.localthemeloader

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** Retained by the detail Activity; a wallpaper-driven resource change must not lose the result. */
class ThemeApplyOperation(context: Context, private val backend: WallpaperBackend = SystemWallpaperBackend(context.applicationContext)) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var observer: ((ApplyResult?, Exception?) -> Unit)? = null
    private var result: ApplyResult? = null
    private var failure: Exception? = null
    private var finished = false
    private var started = false
    fun attach(observer: (ApplyResult?, Exception?) -> Unit) {
        this.observer = observer
        if (finished) observer(result, failure)
    }
    fun detach() { observer = null }
    fun start(theme: ThemePackage, selection: ApplySelection) {
        check(!started); started = true
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            var output: ApplyResult? = null; var error: Exception? = null
            try { output = ThemeApplyManager(appContext, backend).apply(theme, selection) }
            catch (e: Exception) { error = e }
            finally { executor.shutdown() }
            handler.post { result = output; failure = error; finished = true; observer?.invoke(result, failure) }
        }
    }
}

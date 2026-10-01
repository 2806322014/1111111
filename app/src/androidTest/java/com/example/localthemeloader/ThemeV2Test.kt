package com.example.localthemeloader

import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.*
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.*
import java.util.zip.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import androidx.test.core.app.ActivityScenario

@RunWith(AndroidJUnit4::class)
class ThemeV2Test {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val id = "verification_theme_v2"
    private lateinit var repository: ThemeRepository
    private lateinit var importer: ThemeImporter
    @Before fun setup() { repository = ThemeRepository(context); importer = ThemeImporter(repository); repository.delete(id); ThemeApplyStateRepository(context).clear() }
    @After fun cleanup() { repository.delete(id) }
    private fun image(): ByteArray {
        val b = Bitmap.createBitmap(96, 192, Bitmap.Config.ARGB_8888); b.eraseColor(Color.rgb(210, 224, 203))
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it); b.recycle() }.toByteArray()
    }
    private fun archive(code: Int = 1, legacy: Boolean = false, missingLock: Boolean = false, extra: String? = null, mutate: (JSONObject) -> Unit = {}): ByteArray {
        val j = JSONObject().put("schema_version", 2).put("id", id).put("name", "验证主题").put("author", "功能验收").put("version", "$code.0.0").put("version_code", code).put("min_android", 10)
            .put("preview", JSONObject().put("cover", "preview/cover.png").put("widgets", "preview/widgets.png"))
            .put("wallpapers", JSONObject().put("home", "wallpapers/home.png").put("lock", "wallpapers/lock.png"))
            .put("widgets", JSONObject().put("clock", "widgets/clock.json").put("date", "widgets/date.json").put("photo", "widgets/photo.json").put("quote", "widgets/quote.json"))
            .put("icons_dir", "icons").put("accent_color", "#E7B3C2")
        if (legacy) { j.remove("schema_version"); j.remove("widgets"); j.remove("version_code"); j.remove("id") }
        mutate(j)
        val files = linkedMapOf((if (legacy) "theme.json" else "manifest.json") to j.toString().toByteArray(),
            "wallpapers/home.png" to image(), "icons/com.android.settings.png" to image(), "widgets/assets/photo.png" to image())
        if (!missingLock) files["wallpapers/lock.png"] = image()
        WidgetConfigParser.kinds.keys.forEach { kind ->
            val config = JSONObject().put("type", kind).put("background", "#F8E8ED").put("text_color", "#523F47").put("corner_radius", 28)
                .put("time_format", "HH:mm").put("date_format", "MM月dd日").put("show_date", true).put("text", "今天也要好好生活").put("text_size", 18)
            if (kind == "photo") config.put("photo", "widgets/assets/photo.png")
            files["widgets/$kind.json"] = config.toString().toByteArray()
        }
        if (extra != null) files[extra] = "untrusted".toByteArray()
        return ByteArrayOutputStream().also { out -> ZipOutputStream(out).use { zip -> files.forEach { (path, bytes) -> zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry() } } }.toByteArray()
    }
    private fun prepare(bytes: ByteArray) = importer.prepare(ByteArrayInputStream(bytes))
    private fun import(bytes: ByteArray = archive()) = prepare(bytes).use { importer.commit(it) }
    private fun rejected(bytes: ByteArray) { try { prepare(bytes).use { }; fail("Invalid archive accepted") } catch (_: ThemeException) { } }
    @Test fun case01NormalV2Imports() { val t = import(); assertEquals(2, t.schemaVersion); assertEquals(4, t.widgets.size); assertTrue(t.sourceZip.isFile); assertEquals(1, repository.list().count { it.id == id }) }
    @Test fun case02RepositoryReopenPersistsWithoutActivating() { import(); assertEquals("验证主题", ThemeRepository(context).byId(id)?.name); assertNull(ThemeApplyStateRepository(context).get(id)) }
    @Test fun case03BadManifestRejected() { rejected(archive { it.remove("name") }); rejected(archive { it.put("version_code", 0) }); rejected(archive { it.put("version_code", "two") }); rejected(archive { it.put("schema_version", 3) }); assertNull(repository.byId(id)) }
    @Test fun case04ZipTraversalRejected() { rejected(archive(extra = "../escaped.txt")); rejected(archive(extra = "C:/outside.png")); assertFalse(File(context.filesDir, "escaped.txt").exists()); assertNull(repository.byId(id)) }
    @Test fun case05MissingPreviewFallsBack() { val t = import(); assertNotNull(t.homePreview); assertNotNull(t.lockPreview); assertNotNull(t.iconsPreview); assertNotNull(t.widgetsPreview); assertNotNull(ThemeFiles.decode(t.widgetsPreview, 100)) }
    @Test fun case06OfficialWallpaperApplySucceeds() {
        val t = import(); val result = ThemeApplyManager(context).apply(t, ApplySelection())
        assertTrue(result.state.homeWallpaperApplied); assertTrue(result.state.lockWallpaperApplied); assertTrue(result.state.widgetsConfigured)
        assertTrue(WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_SYSTEM) > 0)
        assertEquals("已应用", ThemeApplyStateRepository(context).get(id)?.label)
    }
    @Test fun case07UnsupportedLockKeepsHomeAndReportsPartial() {
        val flags = mutableListOf<Int>(); val backend = object : WallpaperBackend { override fun set(file: File, flag: Int) { flags += flag; if (flag == WallpaperManager.FLAG_LOCK) throw UnsupportedOperationException() } }
        val result = ThemeApplyManager(context, backend).apply(import(), ApplySelection(widgets = false))
        assertEquals(listOf(WallpaperManager.FLAG_SYSTEM, WallpaperManager.FLAG_LOCK), flags); assertTrue(result.state.homeWallpaperApplied); assertFalse(result.state.lockWallpaperApplied)
        assertEquals("部分应用", result.state.label); assertTrue(result.messages.any { it.contains("当前手机暂不支持自动设置该项目") })
    }
    @Test fun case08WidgetConfigParsesAndRenders() {
        val t = import(); val clock = t.widgets.getValue("clock"); assertEquals("HH:mm", clock.timeFormat); assertEquals(28f, clock.cornerRadius)
        assertEquals(Color.parseColor("#523F47"), clock.textColor); assertEquals("今天也要好好生活", t.widgets.getValue("quote").text); assertTrue(t.widgets.getValue("photo").photo!!.isFile)
        WidgetRenderer.providers.forEach { provider -> assertNotNull(WidgetRenderer.views(context, 12345, ComponentName(context, provider), t)) }
    }
    @Test fun case09UnsupportedWidgetPinUsesChineseGuidance() {
        var requests = 0; val backend = object : WidgetPinBackend { override fun supported() = false; override fun request(component: ComponentName, callback: PendingIntent, preview: android.os.Bundle): Boolean { requests++; return true } }
        assertEquals(WidgetPinManager.MANUAL_MESSAGE, WidgetPinManager(context, backend).request(import(), "clock", false)); assertEquals(0, requests)
    }
    @Test fun case10UnsupportedShortcutUsesChineseGuidance() {
        var requests = 0; val backend = object : ShortcutPinBackend { override fun supported() = false; override fun request(info: ShortcutInfo, callback: IntentSender): Boolean { requests++; return true } }
        val manager = ShortcutIconManager(context, backend); val app = manager.apps().first()
        val result = manager.request(import(), app); assertFalse(result.accepted); assertEquals(ShortcutIconManager.UNSUPPORTED_MESSAGE, result.message); assertEquals(0, requests)
    }
    @Test fun case11HigherVersionPreparedThenConfirmedReplacesOne() {
        import(); prepare(archive(2)).use { p -> assertEquals(1, p.previous?.versionCode); assertEquals(1, repository.byId(id)?.versionCode); importer.commit(p) }
        assertEquals(2, repository.byId(id)?.versionCode); assertEquals(1, repository.list().count { it.id == id })
    }
    @Test fun case12FailedUpdateKeepsOldVersion() { import(); rejected(archive(2, missingLock = true)); assertEquals(1, repository.byId(id)?.versionCode); assertTrue(repository.byId(id)!!.sourceZip.isFile) }
    @Test fun case13DeleteAppliedThemeDoesNotResetWallpaper() {
        val t = import(); ThemeApplyManager(context).apply(t, ApplySelection()); val wm = WallpaperManager.getInstance(context)
        val home = wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM); val lock = wm.getWallpaperId(WallpaperManager.FLAG_LOCK)
        assertTrue(repository.delete(id)); assertFalse(t.root.exists()); assertNull(repository.byId(id)); assertNull(ThemeApplyStateRepository(context).get(id))
        assertEquals(home, wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)); assertEquals(lock, wm.getWallpaperId(WallpaperManager.FLAG_LOCK)); assertNotNull(repository.list())
    }
    @Test fun case14LegacyThemeJsonImports() { val t = import(archive(legacy = true)); try { assertTrue(t.id.startsWith("legacy_")); assertEquals(1, t.schemaVersion); assertTrue(t.widgets.isEmpty()); assertTrue(t.icons.isNotEmpty()); assertEquals(t.id, ThemeRepository(context).byId(t.id)?.id) } finally { repository.delete(t.id) } }
    @Test fun equalOrOlderDoesNotOverwrite() { import(archive(2)); prepare(archive(1)).use { try { importer.commit(it); fail("Downgrade accepted") } catch (_: ThemeException) { } }; assertEquals(2, repository.byId(id)?.versionCode) }
    @Test fun canceledUpdateKeepsOld() { import(); prepare(archive(2)).close(); assertEquals(1, repository.byId(id)?.versionCode); assertTrue(repository.workDir.listFiles()?.none { it.name.startsWith("incoming-") } == true) }
    @Test fun brokenImageRejected() { rejected(archive { it.getJSONObject("wallpapers").put("home", "widgets/quote.json") }); assertNull(repository.byId(id)) }
    @Test fun unsafeManifestReferenceRejectedEvenWhenMissing() { rejected(archive { it.getJSONObject("preview").put("cover", "../outside.png") }); assertNull(repository.byId(id)) }
    @Test fun executableThemeContentRejected() { rejected(archive(extra = "extras/run.js")); assertNull(repository.byId(id)) }
    @Test fun clearStateKeepsWallpapersAndLibrary() {
        val t = import(); ThemeApplyManager(context).apply(t, ApplySelection()); val wm = WallpaperManager.getInstance(context); val home = wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        ThemeApplyStateRepository(context).clear(); assertNull(ThemeApplyStateRepository(context).get(id)); assertNull(WidgetConfigurationRepository(context).preferred("clock")); assertNotNull(repository.byId(id)); assertEquals(home, wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM))
    }
    @Test fun deselectedWallpaperNeverCallsBackend() {
        val calls = mutableListOf<Int>(); val backend = object : WallpaperBackend { override fun set(file: File, flag: Int) { calls += flag } }
        val r = ThemeApplyManager(context, backend).apply(import(), ApplySelection(home = false, lock = true, widgets = false))
        assertEquals(listOf(WallpaperManager.FLAG_LOCK), calls); assertFalse(r.state.homeWallpaperApplied); assertTrue(r.state.lockWallpaperApplied); assertEquals("已应用", r.state.label)
    }
    @Test fun staleConfirmationDoesNotOverwriteNewerTheme() {
        import(); prepare(archive(2)).use { p -> import(archive(3)); try { importer.commit(p); fail("Stale confirmation accepted") } catch (_: ThemeException) { } }
        assertEquals(3, repository.byId(id)?.versionCode)
    }
    @Test fun noHomeActivityAndEightWidgetProviders() {
        val home = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(context.packageName), 0)
        assertTrue(home.isEmpty()); val providers = android.appwidget.AppWidgetManager.getInstance(context).installedProviders.filter { it.provider.packageName == context.packageName }
        assertEquals(8, providers.size)
    }
    @Test fun deniedCapabilityQueriesAreSafe() {
        val t = import()
        val widget = object : WidgetPinBackend { override fun supported(): Boolean = throw SecurityException(); override fun request(component: ComponentName, callback: PendingIntent, preview: android.os.Bundle) = true }
        assertEquals(WidgetPinManager.MANUAL_MESSAGE, WidgetPinManager(context, widget).request(t, "clock", false))
        val shortcut = object : ShortcutPinBackend { override fun supported(): Boolean = throw SecurityException(); override fun request(info: ShortcutInfo, callback: IntentSender) = true }
        val manager = ShortcutIconManager(context, shortcut); assertFalse(manager.request(t, manager.apps().first()).accepted)
    }
    @Test fun invalidWidgetUpdatePreservesOldTheme() { import(); rejected(archive(2) { it.getJSONObject("widgets").put("clock", "widgets/quote.json") }); assertEquals(1, repository.byId(id)?.versionCode) }
    @Test fun themeIdCannotCollideWithStateMetadata() {
        try {
            val t = import(archive { it.put("id", "current") })
            val backend = object : WallpaperBackend { override fun set(file: File, flag: Int) { } }
            ThemeApplyManager(context, backend).apply(t, ApplySelection(home = true, lock = false, widgets = false))
            assertEquals("current", ThemeApplyStateRepository(context).get("current")?.themeId)
            assertEquals("已应用", ThemeApplyStateRepository(context).get("current")?.label)
            val other = import(archive { it.put("id", "state_current") })
            ThemeApplyManager(context, backend).apply(other, ApplySelection(home = true, lock = false, widgets = false))
            repository.delete("state_current")
            assertEquals("已应用", ThemeApplyStateRepository(context).get("current")?.label)
        } finally { repository.delete("current"); repository.delete("state_current") }
    }
    @Test fun retainedApplyDeliversToReplacementScreen() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val done = CountDownLatch(1)
        val backend = object : WallpaperBackend { override fun set(file: File, flag: Int) { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) } }
        val operation = ThemeApplyOperation(context, backend); val t = import(); var oldCalled = false; var received: ApplyResult? = null
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { operation.attach { _, _ -> oldCalled = true }; operation.start(t, ApplySelection(home = true, lock = false, widgets = false)) }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        instrumentation.runOnMainSync { operation.detach(); operation.attach { r, _ -> received = r; done.countDown() } }
        release.countDown(); assertTrue(done.await(5, TimeUnit.SECONDS)); assertFalse(oldCalled); assertTrue(received!!.state.homeWallpaperApplied)
    }
    @Test fun oneApplyClickReachesResultDespiteWallpaperConfigurationChanges() {
        val t = import(); val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(ThemeResultActivity::class.java.name, null, false)
        try {
            ActivityScenario.launch<ThemeDetailActivity>(Intent(context, ThemeDetailActivity::class.java).putExtra("theme_id", t.id)).use { scenario ->
                scenario.onActivity { activity ->
                    fun click(v: android.view.View): Boolean {
                        if (v is android.widget.Button && v.text.toString() == "开始应用") { v.performClick(); return true }
                        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) if (click(v.getChildAt(i))) return true
                        return false
                    }
                    assertTrue(click(activity.window.decorView))
                    activity.recreate()
                }
                val result = instrumentation.waitForMonitorWithTimeout(monitor, 15000)
                assertNotNull("One click must open the result after Activity recreation", result)
                instrumentation.runOnMainSync { result?.finish() }
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }
}

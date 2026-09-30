package com.example.localthemeloader

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class ThemeFlowTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var manager: ThemeManager
    private val id = "verification_theme"
    @Before fun setup() { manager=ThemeManager(context); if(manager.currentTheme()?.id==id) manager.restoreDefault(); manager.deleteTheme(id) }
    @After fun cleanup() { if(manager.currentTheme()?.id==id) manager.restoreDefault(); manager.deleteTheme(id) }
    private fun image():ByteArray {
        val bitmap=Bitmap.createBitmap(96,192,Bitmap.Config.ARGB_8888); bitmap.eraseColor(Color.GREEN)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG,100,it); bitmap.recycle() }.toByteArray()
    }
    private fun archive(version:String="1.0.0", name:String="验证主题", extra:String?=null, missingLock:Boolean=false, brokenImage:Boolean=false):ByteArray {
        val json=JSONObject().put("id",id).put("name",name).put("author","测试").put("version",version).put("min_android",10)
        val files=linkedMapOf("manifest.json" to json.toString().toByteArray(),"wallpapers/home.png" to if(brokenImage) byteArrayOf(0,1,2) else image(),"icons/com.android.settings.png" to image())
        if(!missingLock) files["wallpapers/lock.png"]=image()
        if(extra!=null) files[extra]="unsafe".toByteArray()
        return ByteArrayOutputStream().also { out -> ZipOutputStream(out).use { zip -> files.forEach { (path,bytes)-> zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry() } } }.toByteArray()
    }
    private fun import(bytes:ByteArray)=manager.importStream(ByteArrayInputStream(bytes))
    @Test fun importedThemePersistsWithoutActivating() {
        val old=manager.currentTheme()?.id
        val result=import(archive()); assertFalse(result.updated); assertEquals(old,manager.currentTheme()?.id)
        assertEquals("验证主题",ThemeManager(context).themeById(id)?.name)
        assertTrue(result.theme.sourceZip!!.isFile); assertTrue(result.theme.bytes>0)
    }
    @Test fun sameIdUpdateReplacesOneRecord() {
        import(archive()); val result=import(archive("2.0.0","新版主题"))
        assertTrue(result.updated); assertEquals("2.0.0",result.theme.version)
        assertEquals(1,manager.listThemes().count { it.id==id })
    }
    @Test fun fallbackPreviewsAreCreated() {
        val theme=import(archive()).theme
        listOf("cover.png","home.png","lock.png","icons.png").forEach { assertTrue(java.io.File(theme.root,"preview/$it").isFile) }
        assertNotNull(theme.iconsPreview)
    }
    @Test fun brokenUpdateKeepsPreviousVersion() {
        import(archive())
        try { import(archive("2.0.0",missingLock=true)); fail("Missing lock must be rejected") } catch(_:ThemeException) {}
        assertEquals("1.0.0",manager.themeById(id)?.version)
        assertEquals(1,manager.listThemes().count { it.id==id })
    }
    @Test fun zipTraversalIsRejected() {
        try { import(archive(extra="../escaped.txt")); fail("Traversal must be rejected") } catch(_:ThemeException) {}
        assertNull(manager.themeById(id)); assertFalse(java.io.File(context.filesDir,"escaped.txt").exists())
    }
    @Test fun damagedImageIsRejected() {
        try { import(archive(brokenImage=true)); fail("Damaged image must be rejected") } catch(_:ThemeException) {}
        assertNull(manager.themeById(id))
    }
    @Test fun deletingInactiveThemeRemovesResources() {
        val root=import(archive()).theme.root; assertTrue(manager.deleteTheme(id)); assertFalse(root.exists())
    }
    @Test fun sampleThemeIsCompleteAndImportable() {
        Assume.assumeTrue("Sample artwork is supplied separately",context.assets.list("")?.contains("spring_letter.ltheme") == true)
        val result=context.assets.open("spring_letter.ltheme").use { manager.importStream(it) }
        assertEquals("春日来信",result.theme.name); assertEquals("spring_letter",result.theme.id)
        assertTrue(result.theme.icons.size>=30); assertNotNull(result.theme.iconsPreview)
    }
    @Test fun applyAndRestoreKeepLibrary() {
        val theme=import(archive()).theme; val result=manager.applyTheme(theme)
        assertEquals(2,result.wallpaperCount); assertEquals(id,ThemeManager(context).currentTheme()?.id)
        assertFalse(manager.deleteTheme(id)); manager.restoreDefault()
        assertNull(manager.currentTheme()); assertNotNull(manager.themeById(id))
    }
}

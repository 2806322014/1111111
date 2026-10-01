package com.example.localthemeloader

import android.app.AlertDialog
import android.os.Bundle

class SettingsActivity : ThemeScreenActivity() {
    override fun onCreate(state: Bundle?) { super.onCreate(state); render() }
    private fun render() {
        val content = frame("我的拾光"); Ui.add(content, Ui.text(this, "简单、安静，\n把喜欢留在身边。", 30f, Ui.ink, true)); Ui.add(content, Ui.space(this, 24))
        val card = Ui.card(this); Ui.padded(card, 20); Ui.add(card, Ui.text(this, "本机收藏", 20f, Ui.ink, true)); Ui.add(card, Ui.space(this, 12))
        Ui.add(card, Ui.text(this, "主题数量：${repository.list().size} 套\n主题资源总占用：${Ui.mb(repository.bytes())}\n拾光主题  V${BuildConfig.VERSION_NAME}", 15f, Ui.muted)); Ui.add(content, card)
        Ui.add(content, Ui.space(this, 24)); Ui.add(content, Ui.button(this, "清除拾光主题应用状态") {
            AlertDialog.Builder(this).setTitle("清除拾光主题应用状态？").setMessage("清除当前主题状态、组件配置和应用记录，收藏的主题会保留。已有组件显示默认样式，桌面快捷方式需自行移除。\n\n系统不会向第三方应用提供原壁纸备份，原壁纸需要自行重新选择。")
                .setNegativeButton("取消", null).setPositiveButton("清除状态") { _, _ -> background("正在清除状态…", { ThemeApplyStateRepository(applicationContext).clear() }) { render(); toast("应用状态已清除，手机当前壁纸已保留。") } }.show()
        })
        Ui.add(content, Ui.space(this, 24)); Ui.add(content, Ui.text(this, "主题与组件配置只保存在这台设备。无需登录，不上传图片或应用列表。卸载软件会移除收藏，请保留原始主题包。\n\n拾光只设置壁纸、桌面组件与应用快捷方式；锁屏时钟、通知和控制中心由手机系统管理。", 14f, Ui.muted))
        Ui.add(content, Ui.space(this, 18)); Ui.add(content, Ui.button(this, "开源许可") {
            val license = resources.openRawResource(R.raw.material_icons_license).bufferedReader().use { it.readText() }
            AlertDialog.Builder(this).setTitle("Material Icons · Google").setMessage(license).setPositiveButton("关闭", null).show()
        })
    }
}

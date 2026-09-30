# 拾光主题 V1 主题包

`.ltheme` 是标准 ZIP 文件，更改扩展名即可，无需加密。文件需直接位于包根目录，不能额外嵌套一层文件夹。

必备：`manifest.json`、`wallpapers/home.png`、`wallpapers/lock.png` 和 `icons/` 下至少一个可用图标。图片支持 PNG、JPEG、WebP。

```json
{
  "schema_version": 1,
  "id": "spring_letter",
  "name": "春日来信",
  "author": "拾光主题",
  "version": "1.0.0",
  "min_android": 10,
  "description": "主题介绍",
  "wallpapers": {"home": "wallpapers/home.png", "lock": "wallpapers/lock.png"},
  "preview": {"cover": "preview/cover.png", "home": "preview/home.png", "lock": "preview/lock.png", "icons": "preview/icons.png"},
  "icons": {"com.android.settings": "icons/settings.png"},
  "icon_categories": {"default": "icons/base.png"},
  "launcher": {"text_color": "#304E40"}
}
```

- id 为 1–80 个字母、数字、下划线或连字符，首字符为字母或数字。相同 id 始终更新原主题，不创建重复收藏。更新主题资源后需重新点击一键应用以更新系统壁纸。
- 壁纸推荐 1080×2400 或相近竖屏比例。图标推荐 256×256 PNG，保留透明圆角。
- icons 映射中的键为 Android 应用包名，或 `包名/Activity完整类名`。也支持 `icons/包名.png` 自动识别；多个品牌的同类应用可映射到同一资源。
- 未收录的应用使用 default 底板搭配自身原图标，确保可以辨認。主题不修改原厂桌面的图标。
- preview 可省略，软件会从壁纸生成缩略图与图标预览。明确提供的效果图需自行标注模拟时钟、布局等仅供参考。
- 保留对旧版 `theme.json`、`previews/` 主题包的读取支持。新制主题统一使用 manifest.json。
- 导入限压缩包 150 MB、解包 300 MB、单文件 50 MB、最多 2000 个条目。单张图最大边 8192 像素且总像素不超过 2400 万。
- 原始包与资源均保存在软件私有目录；导入后删除外部下载文件不会使主题失效。卸载软件会清除本机收藏。

壁纸更换使用 Android 公共接口，受系统策略约束。锁屏时钟、系统通知和控制中心保持手机系统的行为。

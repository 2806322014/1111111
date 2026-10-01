# .ltheme V2 资源协议

.ltheme 为标准 ZIP，UTF-8 JSON。文件直接放在包根目录，不额外套一层文件夹。所有资源路径从包根目录开始，不能使用绝对路径、反斜杠、冒号、空路径段或 . / .. 路径段。

```text
manifest.json
preview/cover.png lock.png home.png widgets.png icons.png
wallpapers/home.png lock.png
widgets/clock.json date.json photo.json quote.json
widgets/assets/
icons/*.png
colors/palette.json
extras/
```

```json
{
  "schema_version": 2,
  "id": "example_theme",
  "name": "示例搭配",
  "author": "主题作者",
  "version": "2.0.0",
  "version_code": 2,
  "min_android": 10,
  "description": "主题介绍",
  "preview": {"cover":"preview/cover.png", "lock":"preview/lock.png", "home":"preview/home.png", "widgets":"preview/widgets.png", "icons":"preview/icons.png"},
  "wallpapers": {"home":"wallpapers/home.png", "lock":"wallpapers/lock.png"},
  "widgets": {"clock":"widgets/clock.json", "date":"widgets/date.json", "photo":"widgets/photo.json", "quote":"widgets/quote.json"},
  "icons_dir": "icons",
  "accent_color": "#789D80"
}
```

必须有可读取的信息文件、安全 id、非空名称/版本、正整数 version_code，以及两张有效壁纸。schema_version 支持 1、2；id 为 1–80 个字母/数字/下划线/连字符，首字符为字母或数字。名称≤80、作者≤80、版本≤30、说明≤2000 字。V2 的 version_code 必须是 JSON 整数，不使用版本字符串比较；同 id 高版本校验完成后等待确认，同/低版本不覆盖。

预览图可缺省或缺失：锁屏/桌面回退到相应壁纸，封面回退到桌面壁纸；图标与组件有资源时生成示意图。没有组件显示“该主题暂无组件”。明确引用的不安全路径仍会拒绝，已存在但损坏的图片不会进入库。

PNG/JPEG/WebP。壁纸推荐 1080×2400，图标推荐 256×256。预览只供搭配参考，不代替系统时钟或实际桌面布局。

## 组件配置

```json
{"type":"clock", "background":"#E5ECDC", "background_image":"", "text_color":"#315741", "text_size":38, "time_format":"HH:mm", "show_date":true, "date_format":"MM月dd日", "corner_radius":28}
```

```json
{"type":"date", "background":"#E5ECDC", "text_color":"#315741", "text_size":18, "date_format":"MM月dd日 EEEE", "corner_radius":28}
```

```json
{"type":"photo", "background":"#E5ECDC", "photo":"widgets/assets/photo.png", "corner_radius":28}
```

```json
{"type":"quote", "background":"#FFF7F4", "text_color":"#6E5960", "text":"今天也要好好生活", "text_size":18, "corner_radius":28}
```

- type 必须与 manifest 中的种类一致，四种均可独立提供。JSON≤32 KB。颜色使用 Android 支持的颜色字符串；字号 10–64，圆角 0–64，文案≤500 字。
- background_image 和 photo 从包根目录引用有效图片；photo_image 是 photo 的兼容别名。照片组件必须有照片。照片按居中裁切显示，圆角随尺寸调整。
- 时间/日期使用有效的日期格式，最多 64 字。TextClock 实时更新，星期名称跟随设备语言。默认不显示联网内容。
- 桌面组件提供 2×2、4×2 两种入口，并允许用户调整尺寸；实际格数、留白受手机桌面管理。
- 组件配置是有限字段的数据，不执行代码，不加载自定义布局、字体或脚本。

## 图标与旧包

icons/包名.png 自动匹配。也保留 V1 的 icons 对象映射：键为包名或 包名/Activity完整类名，值为根目录相对图片路径；icon_categories.default 可作为默认底板。未映射的应用保留原图标叠加主题底色/底板，确保可辨认。图标只作为固定桌面快捷方式，不修改系统原始应用图标。

manifest.json V1、theme.json Legacy、home_wallpaper/lock_wallpaper、preview.png/jpg、previews/home.png/lock.png 继续读取。Legacy 无 id 时由名称与作者生成稳定 id；没有 version_code 的旧包按 1 处理。制作更新包应沿用已导入的 id，并提供更高整数版本号。旧主题无需新增组件即可继续使用壁纸与图标。

colors/palette.json 和 extras 可保存作者附加色板/说明；本版实际颜色由 manifest.accent_color 与各组件字段控制，不执行额外内容。

## 资源与安全

压缩包≤150 MB、解包总量≤300 MB、单文件≤50 MB、最多2000个条目、单路径≤240字符、manifest≤128 KB。图片最大边8192，总像素≤2400万，并验证可解码。拒绝重复路径、目录穿越、程序/脚本文件。外部包只读取资源和有限配置。

原包与解包资源保存在 files/themes/<id>；准备区位于 files/theme_work。替换前备份旧目录，失败恢复，启动时恢复被中断的备份。删除外部下载文件不会影响收藏；卸载会清除私有收藏，所以请自行保留原始包。

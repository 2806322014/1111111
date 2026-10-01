# 拾光主题 V2.0

Android 10+ 本地手机主题美化工具，在 V1 工程上升级。导入 .ltheme → 完整预览 → 选择壁纸/组件 → 查看实际结果 → 按手机桌面的确认步骤添加组件或图标快捷方式。

正式应用身份仍为 com.example.localthemeloader，版本 2.0.0 / 200，compileSdk/targetSdk 35。无 HOME 入口，不申请默认桌面。Debug 使用独立安装身份，避免测试包覆盖正式用户收藏。

## 主要行为

- 锁屏、桌面壁纸独立设置与反馈，只使用 WallpaperManager 的公开能力。
- 时钟、日期、照片、文案四类组件，分别提供 2×2 / 4×2 入口，可调整尺寸。TextClock 由桌面实时显示时间，无联网/常驻服务。
- 支持 requestPinAppWidget 的桌面显示系统确认；其他桌面可长按桌面 → 小组件 → 拾光主题，选择收藏的主题。
- 图标页面展示原图标与主题预览，支持全选、取消和单选。快捷方式逐个请求确认，取消一枚可继续下一枚，点击后启动对应应用。未收录的应用采用原图标与主题底色/默认底板。
- 同 id 只有更高 version_code 才会在完整校验后提示更新；取消、解析失败或失效的确认不会覆盖旧版。
- 删除主题移除私有资源/原始包及对应配置，手机壁纸保留。已添加组件显示默认样式；系统桌面快捷方式需用户手动删除。
- 清除应用状态只清除本机执行记录、组件配置和请求回执。不会重置手机壁纸或冒充恢复之前的壁纸。
- 状态代表拾光最后一次执行记录。组件“已配置”不等于已添加，图标“已请求”不等于已确认。V1 的单一 active_theme 无法证明每项成功，升级时不推断这些结果。

## 工程模块

ThemeRepository 管理 themes 私有目录及删除；ThemeImporter 负责受限解压、准备与确认后替换；ThemeManifestParser / WidgetConfigParser 解析配置；ThemeApplyManager 执行壁纸与组件配置；CompatibilityManager 检测公开能力；ThemeApplyStateRepository 保存记录；WidgetRenderer/Provider/PinManager 和 ShortcutIconManager 接入桌面。各页面独立 Activity，通用视图样式沿用 Ui。

## 构建与测试

JDK 21、Gradle 8.9、Android SDK 35。首次可运行 tools/bootstrap.py 准备本机工具链，已有环境无需重装。Windows 中文工程路径保留现有 android.overridePathCheck 配置。

```powershell
./gradlew.bat assembleDebug lintDebug
./gradlew.bat connectedDebugAndroidTest
./tools/build-release.ps1
```

签名密钥保存在用户的 .codex/signing/shiguang-theme 私有目录，密码由 Windows 用户加密保存，不进入工程、日志或 Git。同一密钥允许 V1→V2 覆盖安装。最终签名包输出 dist/拾光主题-V2.0.apk，V1 包保留。

GitHub Actions 验证编译/检查及 Android 15 仪器测试。只有配置所有者签名 Secrets 时才在 CI 输出签名 release；本地签名包为交付安装包。

主题协议见 docs/theme-format.md；开发前审查见 docs/V2-development-audit.md；最终验证、文件清单和回退说明见 docs/V2-verification.md。

## 边界

不同手机可能限制锁屏、桌面添加、尺寸或重复快捷方式。公开接口无法预先区分锁屏与桌面的所有厂商限制；详情页说明“可尝试”，应用页返回真实结果。没有账号、网络、商城、天气、广告、OEM 私有接口、系统字体/通知栏修改。仅有 SET_WALLPAPER 权限。

参考：[Android 组件说明](https://developer.android.com/develop/ui/views/appwidgets/overview)、[固定组件与系统确认](https://developer.android.com/develop/ui/views/appwidgets/advanced)、[固定快捷方式](https://developer.android.com/develop/ui/compose/system/shortcuts/creating-shortcuts)。

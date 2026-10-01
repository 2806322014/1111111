# PRE-DEVELOPMENT AUDIT

审查起点：V1.0.0 / versionCode 100，分支 shiguang-v1.0，提交 8e7435f61181d6603e1216333e79ecf2fcde5d2c。

现有功能：安全导入 ZIP 主题包、私有目录持久化、主题库、效果预览、壁纸设置、旧格式读取、原子更新、本地签名构建。minSdk 29，compileSdk/targetSdk 35。

旧桌面代码：MainActivity 的 ROLE_HOME 申请、LauncherActivity、AppGridAdapter、AndroidManifest.xml 的 HOME 入口。V2 移除这些实现；正式 applicationId 和签名沿用 V1。

主题格式：manifest.json V1，兼容 theme.json、previews 目录、应用图标映射与默认图标底板。复用原有 themes/theme_work 私有目录、ZIP 路径检查、大小上限与备份替换逻辑。

新增：ThemeRepository、ThemeImporter、ThemeManifestParser、ThemeApplyManager、CompatibilityManager、WidgetConfigParser、ShortcutIconManager、ThemeApplyStateRepository、WidgetConfigurationRepository、八个组件入口及独立详情/结果/图标/设置/组件配置页面。

风险与处理：锁屏无独立公开预检测接口，实际设置结果独立报告；组件与图标由当前桌面确认；旧版本没有完整成功记录，升级时不伪造成功状态；删除与清除状态均不调用清除壁纸；主题版本升级先校验后确认；相同/低版本不覆盖。保留 V1 分支和签名 APK。

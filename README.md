# 拾光主题 V1.0

Android 10+ 的本地主题平台。导入一个 `.ltheme` 文件即可收藏主题、查看封面/锁屏/桌面/图标效果并一键应用。

- 主题与原始整合包保存在软件私有目录，重启后仍可使用。
- 相同 ID 更新原主题；坏包不破坏已保存的主题。
- 主题详情展示作者、版本、占用、预览和设备兼容性。
- 壁纸使用 Android 公共接口，图标由独立的拾光桌面呈现。首次设置默认桌面由系统确认。
- 拾光桌面支持应用搜索、启动应用、实时钟表、安装/卸载刷新；未收录应用保留原图标并使用统一底板。
- 支持删除未使用主题、恢复系统默认壁纸与原始图标、切回其他默认桌面。
- 无登录、无网络权限、无应用列表上传。

恢复系统默认并不恢复应用主题前的自选壁纸。品牌对锁屏壁纸可能有额外限制；应用结果逐项提示。主题不修改系统通知、控制中心或原厂锁屏时钟。

[主题包规范](docs/theme-format.md)

## 构建与验证

JDK 17/21、Gradle 8.9、Android SDK 35。Windows 可运行 `tools/bootstrap.py` 下载并校验开发工具，然后运行 `tools/build-release.ps1` 生成正式签名 APK。该脚本将签名密钥保存在当前用户的 `.codex/signing/shiguang-theme/` 中，密码以 Windows DPAPI 加密保存；密钥不进入仓库。

```text
./gradlew assembleDebug lintDebug
./gradlew connectedDebugAndroidTest
```

GitHub Actions 在 Android 15 模拟器上运行导入、更新、持久保存、安全检查、应用与恢复测试。测试 APK 与签名正式包有不同用途；正式升级始终使用同一份私有签名密钥。设置 `SHIGUANG_KEYSTORE_B64`、`SHIGUANG_STORE_PASSWORD`、`SHIGUANG_KEY_PASSWORD` 仓库 Secrets 后，工作流可生成相同签名的正式包。

样例主题单独提供，若尚未附带 `spring_letter.ltheme`，样例资源测试会明确跳过；其他功能测试仍正常执行。

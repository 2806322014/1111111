# LocalThemeLoader

Android 本地主题加载器原型，目标设备为 vivo / iQOO。

## 功能
- 导入 ZIP 主题包
- 设置桌面与锁屏壁纸
- 申请成为默认桌面（Launcher）
- 在自定义 Launcher 中应用主题图标

> 说明：普通第三方 App 无法直接调用 vivo/OriginOS 未公开的系统私有主题接口。本项目采用 Android 公开 API + 自定义 Launcher 的可落地方案。

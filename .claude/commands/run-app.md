# run-app — 构建并安装到时迹应用到真机

## 说明
构建「时迹」安卓应用并安装到已连接的安卓设备（真机 USB 调试或模拟器）。

## 触发方式
- 输入 `/run-app`
- 用户说"启动应用"、"运行应用"、"安装到手机"、"跑一下"

## 执行步骤

### 第一步：确认设备已连接
```bash
adb devices
```
- 应显示 `设备名 device`（而非 `unauthorized` 或 `offline`）
- 真机需开启：开发者选项 → USB 调试；连接后手机弹窗点"允许"
- 如 `adb` 不在 PATH，用 Android Studio 自带 SDK 的 adb（默认 `C:/Users/李生/AppData/Local/Android/Sdk/platform-tools/adb.exe`）

### 第二步：构建并安装调试包
```bash
cd "d:/桌面/xiaomiAPP" && ./gradlew installDebug
```
（Windows 无 `./gradlew` 时用 `gradlew.bat installDebug`；首次运行会下载 Gradle 和依赖，需要几分钟）

### 第三步：启动应用
```bash
adb shell am start -n com.shiji.trace/.MainActivity
```

### 第四步：确认启动成功
- 手机上应弹出「时迹」应用
- 首次启动会进入授权引导（使用情况访问权限）

## 注意事项
- 首次构建前确保 Android SDK 已配置（`local.properties` 的 sdk.dir 指向 SDK 路径）
- 构建报错先看错误信息定位，常见：Gradle 下载慢、SDK 版本缺失（用 Android Studio 的 SDK Manager 装）
- 本应用是纯单机应用，不联网，安装调试不需要网络权限
- 如设备显示 unauthorized，检查手机是否弹出授权框并点击允许

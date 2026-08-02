# 安装、升级、卸载与恢复

## 安装前

SoundHaptic 会进入 `system_server`、SystemUI、系统桌面和其他已声明系统进程。开始前请确认：

- 设备已经 root，并且能够在模块导致系统进程异常时进入安全模式或禁用模块；
- Xposed 框架实现 Modern API 102；
- 目标是没有物理振动马达的 HyperOS 设备；
- 已降低触感音量和媒体音量，避免首次测试时扬声器过载。

## 校验产物

Release 同时提供 APK 和 `.sha256`。文件名必须匹配，例如：

```powershell
Get-FileHash .\SoundHaptic-v1.6.0.apk -Algorithm SHA256
Get-Content .\SoundHaptic-v1.6.0.apk.sha256
```

只安装 GitHub Releases 中由本仓库发布的产物。不要安装第三方重打包 APK。

## 首次安装

1. 安装 Release APK；
2. 打开 SoundHaptic，确认状态卡能够连接 Xposed 服务；
3. 批准或在模块管理器中手动选择以下作用域：
   - 系统框架 `system`；
   - `com.android.systemui`；
   - `com.miui.home`；
   - `com.android.settings`；
   - `miui.systemui.plugin`；
4. 确认没有额外勾选截屏应用或普通第三方应用；
5. 完整重启设备。只强停伴生应用不会重新注入 `system_server`；
6. 打开界面，从低音量试听，再验证返回、输入法、锁屏和清理动作。

新安装默认使用 31% 音量、38% 基准力度、ERM 启停、252 Hz 和 16 ms。每台设备的扬声器
与机身共振不同，默认值不是安全上限。

## 从开发包迁移

开发包应用 ID 为 `com.weicheng.soundhaptic.debug`，公开 Release 为
`com.weicheng.soundhaptic`，二者可以同时存在，但只应在模块管理器中启用一个。迁移时：

1. 记录开发包参数；
2. 在模块管理器禁用开发包；
3. 安装并启用 Release；
4. 重新设置参数；
5. 重启设备；
6. 确认 Release 正常后卸载开发包。

两个应用 ID 的 RemotePreferences 独立，不会自动迁移。

## 升级

相同 Release 应用 ID 和签名可以直接覆盖安装。升级不会覆盖已保存参数。涉及 Hook 或音频
运行时代码的版本必须完整重启；只修改界面后也建议重启，以避免旧模块实例继续驻留于
系统进程。

## 卸载

1. 先在 Xposed 管理器中禁用模块；
2. 完整重启设备；
3. 确认声音触感已经停止；
4. 再卸载 APK。

直接卸载 APK 不会从已经运行的系统进程中卸载 Java 类；它们会在进程或设备重启后消失。

## 恢复

若启用后发生 SystemUI 重启或无法正常进入系统：

1. 使用框架提供的安全模式禁用全部模块，或进入 root recovery 环境；
2. 禁用 SoundHaptic；
3. 重启并收集上一次启动的框架日志；
4. 不要反复启用，先在 Issue 中提交脱敏日志和准确 ROM 信息。

若只有声音异常，先关闭界面主开关；若界面无法连接，直接在模块管理器禁用并重启。

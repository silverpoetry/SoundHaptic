# 问题排查与日志采集

## 界面显示未连接

1. 确认安装的是 Modern API102 版本；
2. 确认模块已在管理器中启用；
3. 关闭再打开伴生应用；
4. 检查框架服务日志；
5. 不要通过普通 SharedPreferences 文件手工修改 API102 RemotePreferences。

## 参数已保存但系统仍使用旧值

先强停并重新打开伴生应用，确认界面仍显示新值。应用内试听使用新值而系统动作使用旧值时，
说明已注入进程仍保留旧模块实例或远程配置快照。

v1.6.0 使用同步事务后才显示“已应用”。安装了包含 Hook/音频代码的新 APK 后仍必须完整
重启设备；模块类不会自动热替换 `system_server`。

验证最终入口：

```powershell
adb logcat -c
adb shell cmd vibrator_manager feedback -f 1
adb logcat -d -v time -s SoundHaptic:*
```

日志中的 `system stream pulse` 应显示当前 frequency、duration、strength、shape 与 volume。

## 普通应用或输入法没有反馈

- 确认 `system` 作用域已启用并完成重启；
- 搜索 `hooks installed=true` 和 `terminal vibration`；
- 如果只有 SystemUI/桌面有效，重点检查 `VibratorManagerService` Hook；
- 如果应用自己先检查硬件特性且不发请求，当前最小作用域不会注入该第三方应用。

## 返回有效，但锁屏或清理无效

- 确认 SystemUI、桌面和 `miui.systemui.plugin` 作用域；
- 搜索 `MIUI root hooks installed=true`；
- 搜索扩展 ID 209、212 或 213；
- ROM 若改了 `HapticFeedbackUtil` 签名，需要提供完整类/方法证据，不能直接增加业务 Hook。

## 一个动作播放两次或三次

收集同一时间窗口的 `terminal platform`、`terminal vibration`、`terminal MIUI` 和
`duplicate suppressed` 日志。不要通过缩短音频时长掩盖重复入口；重复必须在分发层去除。

## 没有声音或声音走到耳机

- 确认主开关、触感音量和内置扬声器开关；
- 关闭蓝牙/耳机后复测；
- 查看 `AudioTrack rebound to built-in speaker`；
- 检查 ROM 是否禁止息屏或强制可听触感音频属性；
- 出现任何异常大音量时立即停止。

## 建议日志

```powershell
adb logcat -c
# 复现一次
adb logcat -d -v threadtime -s SoundHaptic:* > soundhaptic-log.txt
```

Issue 中同时提供：设备型号/代号、完整 ROM 版本、Android 版本、模块版本、Xposed 框架与
API 版本、动作步骤、预期、实际和脱敏日志。不要上传 framework JAR、厂商 APK 或个人数据。

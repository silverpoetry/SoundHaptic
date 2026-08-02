# 架构与线程模型

## 设计目标

SoundHaptic 解决两个彼此独立的问题：

1. **让 ROM 愿意发出请求**：无马达设备会在属性、客户端或 MIUI 能力门提前返回；
2. **让请求产生反馈**：虚拟能力只放行调用，最终仍需把语义转换成扬声器 PCM。

能力层不播放声音，播放层不猜测具体按钮。两层通过 Android/MIUI 原本的语义调用链连接。

## 组件

### `SoundHapticModule`

API 102 组合根，只根据进程生命周期安装组件。非目标进程立即 `detach()`；系统服务和包
加载回调分别处理服务端与客户端边界。

### `CapabilityHooks`

- 在应用创建前修正 HyperOS 会静态缓存的三个系统属性；
- 让 `SystemVibrator.hasVibrator()` 与 `SystemVibratorManager.getVibratorIds()` 和服务端
  虚拟执行器保持一致；
- 只为桌面补充其私有 `SystemProperties` 包装器。

### `SystemVibratorHooks`

- 在 `VibratorManagerService` 提供虚拟 ID 0 和 `VibratorInfo`；
- Hook 平台语义入口和 `vibrateInternal`；
- 使用线程局部深度防止语义入口内部再次进入原始入口而重复播放；
- 在 `cancelVibrate` 时取消尚未执行的多段序列。

### `MiuiHapticHooks`

- 放行 `HapticFeedbackUtil` 的五个明确能力方法；
- 构造后修正两个已缓存马达字段并预热播放；
- 只消费四个签名明确的语义终点重载。

### `HapticRuntime`

每个注入进程只有一个 `PulsePlayer`。运行时负责上下文获取、32 ms 同效果去重和取消，不
持有具体 PCM 算法。

### 音频层

- `PulsePlayer`：线程安全门面；
- `ModuleHapticRenderer`：配置、语义节奏、常驻流和预热池；
- `PreviewPulseRenderer`：只用于伴生应用试听；
- `WaveformSynthesizer`：纯 PCM 函数；
- `PersistentHapticStream`：`system_server` 低延迟流；
- `PreparedTrackPool`：UI 进程静态轨复用；
- `ScreenStateMonitor`：交互状态下预热、息屏后释放。

## 线程模型

Hook 回调只做轻量映射和入队。所有音轨创建、PCM 排队、增益/路由应用和关闭都在名为
`SoundHapticAudio` 的高优先级 `HandlerThread` 上执行。调用方线程不等待音频播放结束。

`ModuleHapticRenderer.settings` 是不可变 Snapshot 的 `volatile` 引用。API102
RemotePreferences 变化时生成新 Snapshot，再在音频线程统一应用音量和路由。UI 使用同步
`commit()` 完成一整组参数事务后才显示“已应用”，避免跨进程仍停留在旧快照时给出成功提示。

## 生命周期

```text
进程加载
  └─ 安装最小 Hook
       └─ 第一次构造/请求
            └─ 创建单例 PulsePlayer
                 ├─ 读取 RemotePreferences
                 ├─ 注册配置监听
                 └─ 预热音轨

屏幕关闭 ──► 延迟释放 keep-alive
屏幕点亮 ──► 重新预热
进程退出 ──► 系统回收全部进程内资源
```

模块更新不会热替换已经注入 `system_server` 的类，因此升级 Hook/音频代码后必须重启设备。

## 故障隔离

- Hook 使用 API102 protective exception mode；
- 私有类缺失时记录错误并保留原系统行为；
- 音频失败在播放器内部捕获，并优先回退到单次预览渲染器；
- 不修改磁盘系统属性，不替换系统 APK，不向系统分区写文件。

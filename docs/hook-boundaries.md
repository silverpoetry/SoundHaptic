# Hook 边界和语义映射

## 为什么不能只 Hook 一个 API

Android 的普通应用触感最终进入系统振动服务；HyperOS 的部分系统界面先经过私有能力
判断，再从 `HapticFeedbackUtil` 下发扩展语义。无马达设备还可能在请求到达服务前由
`hasVibrator()` 或静态属性缓存提前退出。因此最小完整边界是“能力门 + 两类最终语义入口”，
而不是某一个万能方法。

## 能力边界

### 系统属性

只在已授权 HyperOS UI 进程覆写读取结果，不写入真实系统属性：

| 属性 | 对进程呈现的值 | 目的 |
|---|---|---|
| `ro.odm.mm.vibrator.motor_not_support` | `false` | 取消无马达短路 |
| `sys.haptic.motor` | `linear` | 选择线性马达语义路径 |
| `sys.haptic.version` | `2.0` | 放行 HyperOS Haptic V2 |

### Framework 客户端

- `SystemVibrator.hasVibrator()` 返回 `true`；
- `SystemVibratorManager.getVibratorIds()` 在系统返回空时提供 ID 0。

### 服务端

`VibratorManagerService` 在没有真实执行器时提供虚拟 ID 0 和声明常用预定义效果、primitive
与 composition 能力的 `VibratorInfo`。它只负责保持能力查询一致，声音仍由最终入口播放。

### MIUI

只放行 `HapticFeedbackUtil` 的以下能力族：

- `isSupportLinearMotorVibrate()`；
- `isSupportZLinearMotorVibrate()`；
- `isSupportHapticVersion2()`；
- `isSupportLinearMotorVibrate(int)`；
- `isSupportExtHapticFeedback(int)`。

构造后修正 `mIsSupportLinearMotorVibrate` 和 `mIsSupportZLinearMotorVibrate` 两个实例缓存。

## 最终分发边界

### Android 服务端

- `performHapticFeedbackWithEffect(...)`：保留 HapticFeedback constant 的平台语义；
- `vibrateInternal(...)`：接收应用直接构造的 predefined、composition 或 waveform 请求；
- `cancelVibrate(...)`：取消尚未播放的多段序列。

语义入口内部通常继续进入 `vibrateInternal`。模块使用 `ThreadLocal` 深度标记阻止同一请求
在两层重复转换。

### MIUI 语义终点

只 Hook `HapticFeedbackUtil` 中参数以 `VibrationAttributes` 开头的四种明确终点签名：

- 两个 `performExtHapticFeedback(...)` 重载；
- 两个 `performHapticFeedback(...)` 重载。

它们已经携带 key 或扩展 ID，能够区分锁屏、清理和普通交互。模块不继续向下 Hook
`VibrateUtils`，因为更底层会丢失部分语义，而且容易和平台服务端重复。

## 映射原则

- Android HapticFeedback constants 映射到稳定的 `HapticEffect`；
- predefined segment 识别 CLICK、DOUBLE_CLICK、TICK、THUD/HEAVY_CLICK、POP 与
  TEXTURE_TICK；
- composition primitive 识别 CLICK、THUD/SPIN、TICK、LOW_TICK 与快速变化类效果；
- MIUI key 依据明确关键词映射；
- MIUI 扩展 ID 209/212/213 分别映射锁屏、通知清理和最近任务清理；
- 未知但合法的触感回退为普通 CLICK；明确 `NO_HAPTICS` 返回空。

长时长请求不会自动变成多连击。只有语义本身是双击、锁屏或清理序列时才安排多个脉冲。

## 明确拒绝的做法

- 全局 Hook 所有 `View.performHapticFeedback`；
- 按方法名模糊扫描所有 `vibrate*`；
- 为返回键、锁屏按钮、清理按钮逐个写业务补丁；
- 通过延时器猜测某个手势是否发生；
- 同时在 MIUI 上层、`VibrateUtils` 和 `system_server` 播放。

这些做法会扩大作用域、制造双击/三击、增加 ROM 更新脆弱性，并让问题无法被实锤定位。

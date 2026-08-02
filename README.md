# SoundHaptic

<p align="center">
  <img src="app/src/main/res/drawable-nodpi/ic_launcher_art.png" width="112" alt="SoundHaptic 图标">
</p>

<p align="center">
  面向无振动马达 Android 设备的扬声器触感反馈模块
</p>

<p align="center">
  <a href="README_EN.md">English</a> ·
  <a href="docs/installation.md">安装指南</a> ·
  <a href="docs/compatibility.md">兼容性</a> ·
  <a href="docs/troubleshooting.md">问题排查</a>
</p>

<p align="center">
  <a href="https://github.com/silverpoetry/SoundHaptic/actions/workflows/ci.yml"><img src="https://github.com/silverpoetry/SoundHaptic/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="https://github.com/silverpoetry/SoundHaptic/releases"><img src="https://img.shields.io/github/v/release/silverpoetry/SoundHaptic?display_name=tag" alt="Release"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="MIT License"></a>
  <img src="https://img.shields.io/badge/Modern%20Xposed-API%20102-5965a8" alt="Modern Xposed API 102">
</p>

SoundHaptic 是一个 Modern Xposed API 102 模块。它面向没有物理振动器、但扬声器能够
在机身上产生可感知短脉冲的 Android 平板，将 Android 与 HyperOS 的触感请求转换为
独立音量、低延迟的机械感 PCM 反馈。

模块不是循环播放一段“马达录音”。它保留点击、刻度、键盘、重击、双击、开关、锁屏
和清理等语义，再为每种语义生成不同的短脉冲、力度层级和节奏。最终目标是让原本会因
`hasVibrator=false` 被系统丢弃的触感，沿 ROM 的正常调用路径到达一个统一渲染入口。

> [!WARNING]
> 本项目需要 root、兼容 Modern Xposed API 102 的框架以及 HyperOS 私有接口。扬声器并非
> 振动马达；过高音量可能产生破音、拍边或刺耳声。首次使用必须从低音量开始。本项目与
> Xiaomi、Android、LSPosed 或 libxposed 项目无关。

## 功能

- 为无执行器设备声明一个虚拟振动器，放行 Android 与 HyperOS 原生触感能力判断；
- 在 `system_server` 最终振动入口统一接收普通应用、输入法和平台触感；
- 在 MIUI `HapticFeedbackUtil` 的明确语义终点接收锁屏、通知清理、最近任务清理等扩展触感；
- 区分纹理刻度、普通刻度、键盘按下/释放、点击、重击、双击、开关和多段系统效果；
- 使用常驻流式 `AudioTrack` 与预热轨道池降低首次播放和连续输入延迟；
- 触感音量由 `AudioTrack` 增益独立控制，不跟随媒体音量键；
- 可强制绑定内置扬声器，避免耳机或蓝牙设备突然播放触感脉冲；
- Material 3 大屏设置界面，支持实时参数、独立试听和扫频测试；
- 无网络权限、无遥测、无广告、无后台上传。

## 默认配置

v1.6.0 的新安装默认值来自目标平板的实机听感定稿：

| 参数 | 默认值 |
|---|---:|
| 触感音量 | 31% |
| 基准力度 | 38% |
| 波形 | ERM 启停 |
| 共振频率 | 252 Hz |
| 基准时长 | 16 ms |
| 输出 | 内置扬声器 |

默认值只用于首次创建配置。升级不会覆盖用户已经保存的参数。

## 兼容性

| 环境 | 状态 |
|---|---|
| HyperOS / Android 16 / `sheng` (`24018RPACC`) | 实机开发与验证 |
| Modern Xposed API 102 | 必需 |
| 其他 Android 16 HyperOS 平板 | 可尝试，私有类和扩展 ID 可能不同 |
| AOSP、MIUI 老版本、非小米 ROM | 未验证，不承诺兼容 |
| 自带振动马达的设备 | 不属于项目目标，不建议启用 |

截屏等动作是否触发触感由 ROM 自身决定。模块只放行能力并消费最终请求，不针对每个按钮、
手势或业务类堆叠补丁。完整边界见[兼容性说明](docs/compatibility.md)。

## 安装

1. 从 [Releases](https://github.com/silverpoetry/SoundHaptic/releases) 下载 APK 与同名
   `.sha256` 文件；
2. 校验 SHA-256；
3. 安装 APK，在模块界面连接 Xposed 服务并批准建议作用域；
4. 在管理器确认模块已启用；
5. 完整重启设备，使 `system_server` 和系统界面重新加载模块；
6. 从较低触感音量开始测试返回、键盘、锁屏与清理动作。

```powershell
Get-FileHash .\SoundHaptic-v1.6.0.apk -Algorithm SHA256
```

开发期 `.debug` 包与公开 Release 使用不同应用 ID，可以共存。详细升级、卸载与恢复步骤见
[安装指南](docs/installation.md)。

## 最小 Hook 架构

```text
应用 / 输入法 / SystemUI / 桌面
               │
               ├── Android 标准触感 ──► VibratorManagerService 最终入口
               │
               └── MIUI 扩展触感 ────► HapticFeedbackUtil 语义终点
                                      │
                                      ▼
                                HapticRuntime
                         去重、语义映射、取消序列
                                      │
                                      ▼
                              ModuleHapticRenderer
                       常驻流 / 预热池 / PCM 合成 / 路由
                                      │
                                      ▼
                                  内置扬声器
```

模块刻意只保留三类 Hook：

1. **能力放行**：系统属性、客户端 `hasVibrator`、虚拟 `VibratorInfo` 与 MIUI 五个能力门；
2. **最终分发**：`system_server` 的语义/原始振动入口，以及 MIUI 四个明确语义重载；
3. **生命周期**：构造时预热和取消请求，不 Hook 具体返回键、锁屏视图或桌面按钮。

这样既能覆盖普通应用和输入法，也避免同一个动作在多层调用链上被重复播放。更完整的类级
职责、线程模型和去重策略见[架构文档](docs/architecture.md)与
[Hook 边界说明](docs/hook-boundaries.md)。

## 代码结构

```text
app/src/main/java/com/weicheng/soundhaptic/
├── SoundHapticModule.java       API 102 组合根
├── HapticSettings.java         RemotePreferences 唯一配置契约
├── PulsePlayer.java            UI 与 Hook 共用音频门面
├── hook/                       能力、映射与最终入口
├── audio/                      语义波形、流式播放和轨道生命周期
└── ui/                         Material 3 设置、作用域和试听
```

## 构建与验证

需要 JDK 17、Android SDK 36 和网络可访问的 Maven 仓库：

```powershell
.\gradlew.bat --no-daemon clean testDebugUnitTest lintRelease assembleRelease
```

开发产物与校验文件：

```powershell
.\tools\build.ps1
```

CI 会校验 Gradle Wrapper、运行单元测试、Lint 并编译 Release。标签发布工作流要求仓库配置
独立 Release 签名 Secrets；签名与可复现发布步骤见[发布签名](docs/release-signing.md)。

## 隐私与安全

- Manifest 未声明 `INTERNET` 权限；
- 不收集设备标识、输入内容、触感历史或音频；
- 配置保存在 Modern Xposed RemotePreferences；
- 日志只记录 Hook、效果名称和音频参数，不记录键盘文本；
- Protective exception mode 防止模块异常传播到 `system_server`。

详见[隐私说明](PRIVACY.md)和[安全策略](SECURITY.md)。

## 文档

- [安装、升级、卸载与恢复](docs/installation.md)
- [兼容性与已知限制](docs/compatibility.md)
- [架构与线程模型](docs/architecture.md)
- [Hook 边界和语义映射](docs/hook-boundaries.md)
- [波形合成与低延迟播放](docs/haptic-rendering.md)
- [问题排查与日志采集](docs/troubleshooting.md)
- [Release 签名与产物验证](docs/release-signing.md)

## 贡献与许可

提交新 ROM 适配时必须提供类签名、调用证据和实机日志，并坚持“能力门 → 统一终点”的最小
Hook 原则。不要提交厂商 APK、反编译产物、完整设备标识或签名密钥。详见
[CONTRIBUTING.md](CONTRIBUTING.md)。

SoundHaptic 以 [MIT License](LICENSE) 发布。第三方组件及许可证见
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

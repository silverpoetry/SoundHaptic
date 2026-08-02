# SoundHaptic

<p align="center">
  <img src="app/src/main/res/drawable-nodpi/ic_launcher_art.png" width="112" alt="SoundHaptic icon">
</p>

<p align="center">
  Speaker-generated haptic feedback for Android devices without a vibration motor
</p>

<p align="center">
  <a href="README.md">简体中文</a> ·
  <a href="docs/installation.md">Installation</a> ·
  <a href="docs/compatibility.md">Compatibility</a> ·
  <a href="docs/troubleshooting.md">Troubleshooting</a>
</p>

SoundHaptic is a Modern Xposed API 102 module for Android tablets that have no physical
vibrator but can produce a perceptible chassis impulse through their built-in speakers. It
converts Android and HyperOS haptic requests into low-latency, independently controlled PCM
pulses.

It does not replay a motor recording. The module preserves semantic differences between ticks,
keyboard presses, clicks, heavy clicks, double clicks, toggles, screen locking and cleanup
actions, then synthesizes an appropriate pulse envelope and rhythm for each effect.

> [!WARNING]
> This project requires root, a compatible Modern Xposed API 102 framework and private HyperOS
> interfaces. A speaker is not a vibration motor. Excessive gain can cause audible distortion or
> mechanical stress. Start at a low haptic volume. This project is not affiliated with Xiaomi,
> Android, LSPosed or libxposed.

## Highlights

- Exposes a virtual vibrator so normal Android and HyperOS capability checks can proceed;
- receives app, keyboard and platform haptics at the final `system_server` vibration boundary;
- receives Xiaomi extended effects at explicit `HapticFeedbackUtil` semantic terminals;
- preserves multiple semantic profiles instead of turning every request into one click;
- uses a persistent streaming `AudioTrack` and prewarmed tracks for low startup latency;
- controls haptic gain independently from the media volume keys;
- can bind playback to the built-in speaker instead of headphones or Bluetooth routes;
- provides a responsive Material 3 settings and test interface;
- requests no network permission and contains no telemetry, ads or upload path.

## Validated default profile

Fresh v1.6.0 installations use the following profile, selected on the target tablet through
device testing:

| Setting | Default |
|---|---:|
| Haptic volume | 31% |
| Reference strength | 38% |
| Envelope | ERM start/stop |
| Resonance frequency | 252 Hz |
| Reference duration | 16 ms |
| Route | Built-in speaker |

Defaults are only used when a preference has never been stored. Upgrades do not overwrite an
existing user profile.

## Requirements and compatibility

- Root and a Modern Xposed framework implementing API 102;
- HyperOS on Android 16 for the currently verified private API layout;
- scopes: `system`, `com.android.systemui`, `com.miui.home`, `com.android.settings` and
  `miui.systemui.plugin`;
- current device evidence: codename `sheng`, model `24018RPACC`.

Other Android 16 HyperOS tablets may work, but AOSP, older MIUI releases and non-Xiaomi ROMs have
not been validated. Devices with a real vibrator are outside the project scope.

## Installation

1. Download the APK and matching `.sha256` file from
   [Releases](https://github.com/silverpoetry/SoundHaptic/releases).
2. Verify the checksum.
3. Install the APK, enable the module and approve the recommended scopes.
4. Reboot the complete device so `system_server` and UI processes reload the module.
5. Begin with a low haptic volume and test navigation, keyboard, lock-screen and cleanup effects.

Development `.debug` packages and public releases use different application IDs and can coexist.
See the [installation guide](docs/installation.md) for upgrade and recovery instructions.

## Architecture

```text
apps / IME / SystemUI / launcher
             │
             ├── Android haptics ──► final VibratorManagerService boundary
             │
             └── MIUI haptics ────► explicit HapticFeedbackUtil terminals
                                    │
                                    ▼
                              HapticRuntime
                       deduplication and mapping
                                    │
                                    ▼
                           ModuleHapticRenderer
                    stream / pool / PCM / route control
                                    │
                                    ▼
                              built-in speaker
```

The module deliberately limits itself to capability gates, final semantic dispatch and audio
lifecycle hooks. It does not hook individual Back buttons, lock-screen views or launcher actions.
This keeps app/IME coverage while preventing duplicate output at multiple levels of one call
chain. See [architecture](docs/architecture.md) and [hook boundaries](docs/hook-boundaries.md).

## Build

JDK 17 and Android SDK 36 are required:

```powershell
.\gradlew.bat --no-daemon clean testDebugUnitTest lintRelease assembleRelease
```

CI validates the wrapper, runs unit tests and Lint, and compiles a release APK. Tag builds require
dedicated signing secrets as documented in [release signing](docs/release-signing.md).

## Privacy, security and license

SoundHaptic has no `INTERNET` permission and does not collect identifiers, typed content, haptic
history or audio. Configuration is stored through Modern Xposed RemotePreferences. See
[PRIVACY.md](PRIVACY.md) and [SECURITY.md](SECURITY.md).

Contributions must preserve the minimal-hook design and include reproducible evidence for new ROM
paths. See [CONTRIBUTING.md](CONTRIBUTING.md).

SoundHaptic is released under the [MIT License](LICENSE). Third-party notices are available in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

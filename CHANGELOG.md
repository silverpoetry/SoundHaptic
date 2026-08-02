# Changelog

All notable changes are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and releases use semantic versioning.

## [1.6.0] - 2026-08-02

### Added

- Modern Xposed API 102 module and companion application.
- Virtual vibrator capability for devices without a physical actuator.
- Final `system_server` semantic and raw vibration dispatch.
- MIUI capability gates and four explicit `HapticFeedbackUtil` semantic terminals.
- Android, predefined, composition primitive and Xiaomi extended-effect mapping.
- Persistent stream, prewarmed track pool, built-in-speaker routing and screen lifecycle handling.
- Material 3 large-screen settings UI, live preview and frequency sweep.
- API102 RemotePreferences settings shared across hooked processes.
- CI, signed tag release workflow, unit tests and project documentation.

### Changed

- Validated fresh-install defaults are now 31% volume, 38% reference strength, ERM start/stop,
  252 Hz and 16 ms.
- Remote settings use a synchronous transaction before the UI reports that a change was applied.

### Known limitations

- Compatibility is currently verified only on Android 16 HyperOS device `sheng` (`24018RPACC`).
- ROM actions that never emit a vibration request cannot be synthesized by the module.
- Speaker response and perceived chassis feedback vary materially between devices.

[1.6.0]: https://github.com/silverpoetry/SoundHaptic/releases/tag/v1.6.0

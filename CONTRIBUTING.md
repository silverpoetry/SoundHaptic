# Contributing to SoundHaptic

SoundHaptic runs inside `system_server` and privileged HyperOS UI processes. Correctness, bounded
scope and recoverability take precedence over broad but unverified coverage.

## Development environment

- JDK 17;
- Android SDK 36;
- a Modern Xposed API 102 test environment;
- a device that can be recovered if a system process boot-loops.

Run the full local check before opening a pull request:

```powershell
.\gradlew.bat --no-daemon clean testDebugUnitTest lintRelease assembleRelease
```

## Design rules

1. Prefer the narrowest stable capability gate or final semantic boundary.
2. Do not Hook individual buttons, views or gestures when the request has a shared final entry.
3. Do not scan every `View.performHapticFeedback` or every method named `vibrate`.
4. Preserve semantic effects; do not infer double or triple clicks from duration alone.
5. Keep PCM synthesis pure and independent from Android process lifecycle code.
6. Keep all remote configuration keys in `HapticSettings`.
7. Never let module failures escape into `system_server`; use protective Hook mode and local error
   handling.
8. New keep-alive or polling work must document latency, lifetime and power impact.

## Evidence required for ROM support

- exact ROM, Android version, device codename and package version;
- target class and complete method signature;
- log evidence that the method is a capability gate or final dispatch boundary;
- before/after behavior from a real device;
- evidence that one user action produces one semantic conversion;
- fallback behavior when the private API is absent.

Do not submit vendor APKs, framework JARs, decompiled sources, full serial numbers, personal MAC
addresses, account data or signing material.

## Code and commits

- Java uses 4 spaces; Markdown, XML and YAML use 2 spaces;
- use LF except Windows batch files;
- keep classes focused on one responsibility;
- add tests for pure mapping, settings or synthesis behavior;
- use short imperative commit subjects;
- explain impact, root cause, evidence and validation in the pull request.

By contributing, you agree that your contribution may be distributed under the MIT License.

# Privacy

SoundHaptic is an offline system module.

## Data handled

The companion application stores only these user-selected settings through Modern Xposed
RemotePreferences: enabled state, haptic volume, frequency, reference duration, reference strength,
waveform and built-in-speaker routing.

The hooked runtime receives haptic effect identifiers and timing data needed to select a local PCM
profile. It does not inspect or store typed text.

## Data not collected

- no network permission;
- no analytics, telemetry, advertising or crash-reporting SDK;
- no account, location, contact or media access;
- no recording or microphone permission;
- no upload endpoint;
- no persistent haptic history.

Diagnostic logcat entries may contain process/package names, semantic effect names and configured
audio parameters. Review logs before sharing them publicly.

Disabling the module stops new Hook execution after affected processes restart. Uninstalling the
application removes its application-side data; the Xposed framework may retain module metadata
according to its own lifecycle.

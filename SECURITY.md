# Security policy

## Supported versions

| Version | Security updates |
|---|---|
| 1.6.x | Yes |
| Development/debug builds | Best effort |
| Earlier private test builds | No |

## Reporting a vulnerability

Please use GitHub's private vulnerability reporting for this repository. If that option is not
available, contact the repository owner through the private contact method on the GitHub profile.
Do not open a public issue for a vulnerability that can crash or boot-loop a privileged process,
route sound to an unintended device, expose signing material or enable arbitrary code execution.

Include the affected version, ROM and device, reproduction steps, relevant logs with personal data
removed, impact and any proposed mitigation. Allow a reasonable remediation window before public
disclosure.

## Operational safety

- Keep a recovery path before enabling any module that injects into `system_server`.
- Install only artifacts from this repository's Releases and verify SHA-256.
- Never reuse the public Android debug keystore for release signing.
- Begin at low haptic volume; stop immediately if the speaker distorts or mechanically bottoms out.
- Scope the module only to documented packages.

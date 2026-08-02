# Release 签名与产物验证

## 原则

公开 Release 必须使用项目专用、不可公开的 Android keystore。禁止使用 Android 默认 debug
keystore，也禁止把 keystore、密码或 Base64 内容提交到仓库。

丢失 keystore 或 key password 将无法为同一应用 ID 发布可覆盖安装的更新，因此至少保留两份
加密离线备份，并单独记录证书指纹。

## 本地环境变量

`app/build.gradle` 只在四个变量全部存在时签名 Release：

- `SOUNDHAPTIC_KEYSTORE_PATH`
- `SOUNDHAPTIC_KEYSTORE_PASSWORD`
- `SOUNDHAPTIC_KEY_ALIAS`
- `SOUNDHAPTIC_KEY_PASSWORD`

```powershell
$env:SOUNDHAPTIC_KEYSTORE_PATH='C:\secure\soundhaptic-release.jks'
$env:SOUNDHAPTIC_KEYSTORE_PASSWORD='...'
$env:SOUNDHAPTIC_KEY_ALIAS='soundhaptic'
$env:SOUNDHAPTIC_KEY_PASSWORD='...'
.\gradlew.bat --no-daemon clean testDebugUnitTest lintRelease assembleRelease
```

没有这些变量时，Gradle 仍可编译未签名 Release，供 CI 验证源码，但不能直接安装或发布。

## GitHub Secrets

标签发布工作流需要：

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_KEYSTORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

PowerShell 生成 Base64：

```powershell
[Convert]::ToBase64String(
  [IO.File]::ReadAllBytes('C:\secure\soundhaptic-release.jks')
) | Set-Clipboard
```

GitHub Actions 在临时目录恢复 keystore，构建后使用 SDK `apksigner` 校验证书，再创建 APK 和
SHA-256 文件。临时 Runner 结束后不会保留 keystore。

## 发布流程

1. 更新 `versionCode`、`versionName` 和 `CHANGELOG.md`；
2. 在 `main` 上通过 CI；
3. 创建与 `versionName` 完全一致的标签，例如 `v1.6.0`；
4. 推送标签；
5. Release workflow 运行测试、Lint、签名、`apksigner verify` 和 SHA-256；
6. 从 GitHub Release 重新下载产物并在干净设备验证覆盖安装。

## 证书与 APK 验证

正式 Release 证书 SHA-256：

```text
d2041b930935164722360a1b99a9433adfbc9bc0e20627f70defadb0b214c25b
```

```powershell
apksigner verify --verbose --print-certs .\SoundHaptic-v1.6.0.apk
Get-FileHash .\SoundHaptic-v1.6.0.apk -Algorithm SHA256
```

发布后不要重写同一个 tag 的二进制产物。发现错误应增加版本号并发布新版本。

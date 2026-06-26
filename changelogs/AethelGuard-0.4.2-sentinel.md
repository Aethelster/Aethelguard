# AethelGuard v0.4.2-sentinel — Monitor-Green PIN Texture Patch

This Free update is a small, focused polish release for server owners using the built-in PIN GUI themes. It keeps the Free security feature set unchanged and fixes a visual texture issue in the monitor-green keypad theme. 🛡️

## ✨ What changed

- Fixed the monitor-green PIN GUI digit `5` head texture value.
- Rebuilt the release JAR from the PascalCase AethelGuard source and metadata.
- Updated the release asset name so it now follows the product casing: `AethelGuard-0.4.2-sentinel.jar`.

## 🛠️ Technical details

- Corrected the extra Base64 padding character in the monitor-green `g5` texture entry.
- Verified that the v0.4.2 release JAR does not include Premium-only classes or resources.
- Confirmed the v0.4.2 release source diff is limited to the intended texture patch, version metadata, and release documentation.
- Confirmed the plugin metadata now uses:
  - `name: AethelGuard`
  - `main: me.aethelster.aethelguard.AethelGuard`
  - `version: 0.4.2-sentinel`

## ✅ Stability

This release contains a narrow visual bugfix and keeps the Free auth/security behavior unchanged. The latest published Free stable release is `AethelGuard v0.4.2-sentinel`.

## 📦 Assets

- `AethelGuard-0.4.2-sentinel.jar`
- `AethelGuard-0.4.2-sentinel-changelog.md`

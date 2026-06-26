# AethelGuard Shared Release & Changelog Rules

This file is the shared release-note and changelog standard for both AethelGuard Free and AethelGuard Premium.

## Scope

- These rules apply to both `Free` and `Premium`.
- Free and Premium still remain separate projects with separate Git histories, builds, tags, and releases.
- A release for one edition must not silently change the other edition.

## Release Types

### Sub-version releases

Sub-version releases only document what changed in that exact release.

- Free sub-version family: `dev`
- Premium sub-version family: `runic`
- Examples:
  - `AethelGuard v0.3.9-dev`
  - `AethelGuard Premium v0.2.1.2-runic`

### Main-version releases

Main-version releases document every meaningful change since the previous main-version release.

- Free main-version family: `sentinel`
- Premium main-version family: `aegis`
- Examples:
  - `AethelGuard v0.4-sentinel`
  - `AethelGuard Premium v0.2-aegis`

## Changelog File Requirements

- Every build must have a Markdown changelog before the package build is considered complete.
- The changelog file must live under `changelogs/`.
- The changelog file name must match the final build artifact name:
  - `changelogs/<finalName>.md`
- The changelog archive produced by the package build must be copied beside the local JAR archive:
  - `jars/changelogs/<finalName>-changelog.md`
- Changelog files must be written in English.
- Changelog text should be friendly, clear, and useful for server owners, not only developers.
- Emoji may be used, but only where it helps readability and does not make the release look unprofessional.

## Required Changelog Format

Each changelog Markdown file and each GitHub release body must follow this structure:

```md
# AethelGuard <version> — <update name>

Short, friendly summary of what this update is about.

## ✨ What changed

- Clear user-facing change.
- Another clear user-facing change.

## 🛠️ Technical details

- Important implementation detail.
- Migration, config, message, or metadata detail.

## ✅ Stability

Stable status text.

## 📦 Assets

- `<jar-name>.jar`
- `<changelog-name>.md`
```

## Stability Text Rules

- If the user has tested the build and decided it is good, the changelog may say that this version is the recommended stable version.
- If the user has not tested the build yet, the changelog must not call the new version stable.
- If the new version is not confirmed stable, the changelog must clearly state the latest known stable version.

Recommended wording:

- Confirmed stable:
  - `This version has been tested and is the recommended stable build.`
- Not confirmed stable:
  - `This build has not been fully player-tested yet. The latest confirmed stable version remains <version>.`

## Asset Rules

- GitHub release assets must use PascalCase product naming:
  - `AethelGuard-...jar`
  - `AethelGuard-...-changelog.md`
  - `AethelGuard-Premium-...` is not used unless the actual artifact name changes.
- The asset list in the release body must match the uploaded files.
- Old changelog assets must not be silently reused when the release note format changes.

## Current Application

- These rules are applied starting with the currently published latest releases:
  - Free: `AethelGuard v0.4.2-sentinel`
  - Premium: `AethelGuard Premium v0.2.1.2-runic`
- Future releases must continue using this format.

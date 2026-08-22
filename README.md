# Extreme Tube

Extreme Tube is a security-first, open-source custom patch project for a user-supplied official YouTube Android APK. The goal is to preserve the stock YouTube interface while adding an auditable, codec-aware quality selector and a privacy-focused patch profile.

## Current status

The custom patch bundle now compiles successfully in GitHub Actions and includes:

- **Extreme Tube branding**
- **All Formats selector** integrated into YouTube's existing advanced-quality bottom sheet
- Actual adaptive video variants listed separately by resolution, FPS, codec, container and bitrate
- Separate AV1, VP9 and AVC/H.264 entries when YouTube returns them
- HEVC/H.265 entries only when YouTube actually exposes such a stream
- 4K and 8K labels only when the current video's stream metadata contains them
- Exact-itag selection in memory while preserving audio formats
- Fail-open behavior: if metadata or a runtime hook is unavailable, playback is restored instead of leaving a narrowed stream list

The selector and extension compile successfully. A real-device/runtime test still requires a clean supported YouTube APK.

## Locked stable base for first runtime build

For the first runtime test, use the stable Morphe target:

- YouTube version: **21.04.223**
- Package: `com.google.android.youtube`
- Variant: **universal, nodpi, single APK**
- Minimum Android: API 28 / Android 9
- Expected APK file SHA-256: `78571be679f586d11a4e56fb1ce6bf9dfd958ce6b8af786c4a3bd94792ce8c7c`
- Expected Google signing-certificate SHA-256 accepted by Morphe: `3d7a1223019aa39d9ea0e3436ab7c0896bfb4fb679f4de5fe7c23f326c8f994a`

Do not patch a base APK when its package, version, file hash or signing certificate does not match the expected trusted input.

## Project goals

- Preserve the original YouTube UI and behavior as much as technically possible.
- Do not bundle or redistribute the original YouTube APK.
- Use a user-supplied, signature-verified YouTube APK as the patch input.
- Expose actual available video formats without inventing unavailable qualities.
- Keep codec variants separate where available: AV1, VP9 and AVC/H.264; HEVC/H.265 only when the upstream stream data actually provides it.
- Show useful stream metadata such as resolution, FPS, codec, container and bitrate.
- No analytics SDK, advertising SDK, telemetry SDK, credential harvesting, hidden updater, remote-code loader or unrelated permissions in Extreme Tube patch code.
- Keep the source public and reviewable.

## Security model

Extreme Tube patch source does not contain the proprietary YouTube APK. A patched APK is produced from a user-supplied original APK. Never install an APK merely because it has the Extreme Tube name: verify its provenance and signing information.

No software can responsibly be promised to be "100% virus-free" for all time. This repository instead aims for verifiable properties: minimal code, public source, inspectable dependencies, no secret network endpoints, no dynamic code download and reproducible build inputs where practical.

The custom Extreme Tube selector performs no network I/O and does not read or persist stream URLs, cookies, authentication headers, Google account credentials or tokens. It works only with adaptive-format objects already present inside the running YouTube process.

See [SECURITY.md](SECURITY.md) for the security requirements used by this project.

## Build model

The locked build script is `scripts/build-extreme-tube.sh`. It performs no downloads and requires local copies of:

1. Morphe Desktop all-in-one JAR
2. Official Morphe patch bundle (`.mpp`)
3. Extreme Tube patch bundle (`.mpp`)
4. The verified YouTube base APK

The script applies the official Morphe `Clone app`, `GmsCore support`, `Hide ads` and `Video quality` patches plus the Extreme Tube branding and All Formats selector, then produces a separately signed `ExtremeTube.apk` using package `com.extremetube.app`.

GitHub Actions publishes the compiled Extreme Tube `.mpp` as the `ExtremeTube-Patches` workflow artifact after the source build, APK guard and checksum checks pass.

## Legal / upstream notice

This repository is not affiliated with Google, YouTube or Morphe. YouTube is a trademark of Google LLC. Morphe is referenced only to describe patch compatibility and upstream technology.

Morphe-derived/template code is subject to GPLv3 and the Morphe Section 7 naming/branding notice. See [NOTICE](NOTICE).

No Google/YouTube proprietary APK, artwork or proprietary source code should be committed to this repository.

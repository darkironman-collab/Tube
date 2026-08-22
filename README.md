# Extreme Tube

Extreme Tube is a security-first, open-source custom patch project for a user-supplied official YouTube Android APK. The goal is to preserve the stock YouTube interface while adding an auditable, codec-aware quality selector and a privacy-focused patch profile.

## Project goals

- Preserve the original YouTube UI and behavior as much as technically possible.
- Do not bundle or redistribute the original YouTube APK.
- Use a user-supplied, signature-verified YouTube APK as the patch input.
- Expose actual available video formats without inventing unavailable qualities.
- Keep codec variants separate where available: AV1, VP9 and AVC/H.264; HEVC/H.265 only when the upstream stream data actually provides it.
- Show useful stream metadata such as resolution, FPS, HDR/SDR, codec, container and bitrate.
- No analytics SDK, advertising SDK, telemetry SDK, credential harvesting, hidden updater, remote-code loader or unrelated permissions in Extreme Tube patch code.
- Keep the source public and reviewable.

## Security model

Extreme Tube patch source does not contain the proprietary YouTube APK. A patched APK is produced from a user-supplied original APK. Never install an APK merely because it has the Extreme Tube name: verify its provenance and signing information.

No software can responsibly be promised to be "100% virus-free" for all time. This repository instead aims for verifiable properties: minimal code, public source, pinned/inspectable dependencies, no secret network endpoints, no dynamic code download and reproducible build inputs where practical.

See [SECURITY.md](SECURITY.md) for the security requirements used by this project.

## Status

Initial project scaffolding is being built from the official Morphe custom-patches template. The first development target is the YouTube quality pipeline and an **All Formats** selector.

Planned entries include, only when actually available for the current video:

- 4320p / 8K variants
- 2160p / 4K variants
- 1440p variants
- 1080p variants
- 60 FPS and HDR variants
- AV1, VP9 and AVC/H.264 shown independently
- HEVC/H.265 when exposed by the upstream stream data

## Legal / upstream notice

This repository is not affiliated with Google, YouTube or Morphe. YouTube is a trademark of Google LLC. Morphe is referenced only to describe patch compatibility and upstream technology.

Morphe-derived/template code is subject to GPLv3 and the Morphe Section 7 naming/branding notice. See [NOTICE](NOTICE).

No Google/YouTube proprietary APK, artwork or proprietary source code should be committed to this repository.

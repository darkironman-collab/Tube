# Extreme Tube — All Formats quality selector

## Goal

Keep the stock YouTube application UI and playback pipeline, but expose every video format that is actually present in YouTube's adaptive stream metadata instead of collapsing choices only by resolution.

Examples of separate entries when the source provides them:

- `1080p60 HDR · AV1 · WebM · 8.4 Mbps`
- `1080p60 · VP9 · WebM · 7.1 Mbps`
- `1080p · AVC / H.264 · MP4 · 5.8 Mbps`
- `2160p60 HDR · AV1 · WebM`
- `4320p60 · AV1 · WebM`
- `HEVC / H.265` only if an actual `hev1`, `hvc1` or equivalent HEVC stream is present.

No option may be fabricated simply because a device supports a codec or a resolution.

## Confirmed upstream hook points

Current Morphe YouTube patches already fingerprint the YouTube streaming-data constructor and the advanced-quality item click path. Morphe's current quality code also reads the adaptive format list and parses each format's MIME type and height. Extreme Tube should extend this same stream layer rather than replacing the full player UI.

## Planned data model

For each actual video format, collect only playback metadata needed for display and selection:

- itag / format identity
- width and height
- FPS
- MIME type
- codec string
- bitrate
- HDR/color information when present
- container
- audio/video role

A stable internal key must include more than resolution, so AV1/VP9/AVC variants at the same height remain separate.

## Codec labels

Normalize MIME/codec metadata for display only:

- `av01` -> `AV1`
- `vp09` / `vp9` -> `VP9`
- `avc1` / `avc` -> `AVC / H.264`
- `hev1` / `hvc1` / HEVC -> `HEVC / H.265`

Unknown codecs must be shown using their original codec identifier instead of being guessed.

## Selection behavior

1. `Auto` remains available.
2. The stock YouTube quick-quality sheet remains untouched unless the user opens the detailed quality selector.
3. Detailed entries are generated from the current video's actual adaptive formats.
4. Selecting an entry must select that exact format identity, not merely a resolution bucket.
5. If the selected stream is video-only, YouTube's normal compatible audio path must remain active.
6. If the device decoder cannot play a selected format, fail safely and allow the user to choose another format or Auto.

## 8K / HDR policy

`4320p / 8K`, HDR and high-FPS labels are data-driven. The selector must never show them if they are absent from the current video response.

## Security constraints

- No new analytics or telemetry.
- No third-party ad SDK.
- No arbitrary network endpoints.
- No dynamic DEX/JAR/APK download or execution.
- No credential or cookie collection by Extreme Tube code.
- No additional Android permission merely for the quality selector.
- Stream metadata processing occurs in-process from data already supplied to the player.

## Compatibility strategy

The quality implementation will track Morphe's current supported YouTube versions and use fingerprints rather than hard-coded obfuscated class names wherever practical. Experimental YouTube versions stay marked experimental until tested.

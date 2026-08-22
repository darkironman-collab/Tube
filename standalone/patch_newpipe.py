#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else "upstream").resolve()


def patch(path: str, old: str, new: str, count: int = -1):
    p = root / path
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"Expected text not found in {path}: {old[:100]!r}")
    text = text.replace(old, new, count)
    p.write_text(text, encoding="utf-8")
    print(f"patched {path}")

# Independent installed package and v0.2 branding. Namespace remains upstream package so
# source references continue to compile; Android applicationId is fully independent.
patch(
    "app/build.gradle.kts",
    'applicationId = NEWPIPE_APPLICATION_ID_OLD',
    'applicationId = "com.extremetube.app"',
    1,
)
patch(
    "app/build.gradle.kts",
    'resValue("string", "app_name", "NewPipe")',
    'resValue("string", "app_name", "Extreme Tube")',
    1,
)
patch(
    "buildSrc/src/main/kotlin/ProjectConfig.kt",
    'const val NEWPIPE_VERSION_CODE = 1014',
    'const val NEWPIPE_VERSION_CODE = 20',
    1,
)
patch(
    "buildSrc/src/main/kotlin/ProjectConfig.kt",
    'const val NEWPIPE_VERSION_NAME = "0.29.0"',
    'const val NEWPIPE_VERSION_NAME = "0.2.0"',
    1,
)
# Android 37 platform was not available on the hosted runner. The upstream targetSdk is 35,
# and Extreme Tube does not use API-37-only symbols, so compile against the stable API 36 SDK.
patch(
    "buildSrc/src/main/kotlin/ProjectConfig.kt",
    'const val NEWPIPE_VERSION_SDK_COMPILE_MAJOR = 37',
    'const val NEWPIPE_VERSION_SDK_COMPILE_MAJOR = 36',
    1,
)
# Upstream documents this workaround for JitPack deleting extractor artifacts: use an
# abbreviated form of the same commit hash so JitPack regenerates the coordinate.
patch(
    "gradle/libs.versions.toml",
    'teamnewpipe-newpipe-extractor = "4de221bf67ec0bf8dbdf573fbc9d4412a8561cb0"',
    'teamnewpipe-newpipe-extractor = "4de221bf67ec0bf8dbdf573fbc9d4412a8561cb"',
    1,
)

# Own launcher mark: simple red play tile, no Morphe/YouTube APK artwork.
drawable = root / "app/src/main/res/drawable/extreme_tube_icon.xml"
drawable.parent.mkdir(parents=True, exist_ok=True)
drawable.write_text('''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#D90000" android:pathData="M18,18h72a14,14 0,0 1,14 14v44a14,14 0,0 1,-14 14h-72a14,14 0,0 1,-14 -14v-44a14,14 0,0 1,14 -14z"/>
    <path android:fillColor="#FFFFFF" android:pathData="M45,36L76,54L45,72Z"/>
</vector>
''', encoding="utf-8")
patch(
    "app/src/main/AndroidManifest.xml",
    'android:icon="@mipmap/ic_launcher"',
    'android:icon="@drawable/extreme_tube_icon"',
    1,
)
patch(
    "app/src/main/AndroidManifest.xml",
    'android:logo="@mipmap/ic_launcher"',
    'android:logo="@drawable/extreme_tube_icon"',
    1,
)

# Codec-aware + estimated-size labels in the in-player quality menu.
old_quality = '''        for (int i = 0; i < availableStreams.size(); i++) {
            final VideoStream videoStream = availableStreams.get(i);
            qualityPopupMenu.getMenu().add(POPUP_MENU_ID_QUALITY, i, Menu.NONE, MediaFormat
                    .getNameById(videoStream.getFormatId()) + " " + videoStream.getResolution());
        }
'''
new_quality = '''        final long extremeDurationSeconds = player.getCurrentStreamInfo()
                .map(StreamInfo::getDuration).orElse(0L);
        for (int i = 0; i < availableStreams.size(); i++) {
            final VideoStream videoStream = availableStreams.get(i);
            final String extremeCodec = videoStream.getCodec() == null
                    || videoStream.getCodec().isEmpty()
                    ? "codec ?" : videoStream.getCodec();
            final String extremeFps = videoStream.getFps() > 0
                    ? " • " + videoStream.getFps() + "fps" : "";
            final long extremeBytes = videoStream.getBitrate() > 0 && extremeDurationSeconds > 0
                    ? (videoStream.getBitrate() * extremeDurationSeconds) / 8L : 0L;
            final String extremeSize = extremeBytes > 0
                    ? " • ~" + Math.max(1L, Math.round(extremeBytes / 1048576.0d)) + " MB"
                    : "";
            qualityPopupMenu.getMenu().add(POPUP_MENU_ID_QUALITY, i, Menu.NONE,
                    MediaFormat.getNameById(videoStream.getFormatId()) + " "
                            + videoStream.getResolution() + extremeFps + " • "
                            + extremeCodec + extremeSize);
        }
'''
patch(
    "app/src/main/java/org/schabi/newpipe/player/ui/VideoPlayerUi.java",
    old_quality,
    new_quality,
    1,
)
patch(
    "app/src/main/java/org/schabi/newpipe/player/ui/VideoPlayerUi.java",
    '''        player.getSelectedVideoStream()
                .ifPresent(s -> binding.qualityTextView.setText(s.getResolution()));
''',
    '''        player.getSelectedVideoStream().ifPresent(s -> {
            final String codec = s.getCodec() == null || s.getCodec().isEmpty()
                    ? "" : " • " + s.getCodec();
            binding.qualityTextView.setText(s.getResolution() + codec);
        });
''',
    1,
)

# Exact stream-size screen already performs HEAD requests. Add codec/FPS to that row so
# codec-wise size can be inspected before download.
patch(
    "app/src/main/java/org/schabi/newpipe/util/StreamItemAdapter.java",
    '''            final VideoStream videoStream = ((VideoStream) stream);
            qualityString = videoStream.getResolution();

            if (hasAnyVideoOnlyStreamWithNoSecondaryStream) {
''',
    '''            final VideoStream videoStream = ((VideoStream) stream);
            qualityString = videoStream.getResolution();
            if (!isNullOrEmpty(videoStream.getCodec())) {
                qualityString += " • " + videoStream.getCodec();
            }
            if (videoStream.getFps() > 0) {
                qualityString += " • " + videoStream.getFps() + "fps";
            }

            if (hasAnyVideoOnlyStreamWithNoSecondaryStream) {
''',
    1,
)

notice = root / "EXTREME_TUBE_NOTICE.txt"
notice.write_text('''Extreme Tube v0.2.0 standalone build
====================================
Package: com.extremetube.app

This build is independent of the official YouTube Android APK and does not use
Morphe code or Morphe patch bundles. It is built from the GPLv3 NewPipe v0.29.0
source tree with a small, auditable Extreme Tube branding/quality-display patch.
The corresponding patched source is published beside the APK.

Extreme Tube changes:
- Extreme Tube name/package/icon
- codec + FPS shown per video quality
- estimated video payload size in the live quality menu when bitrate is known
- codec + exact HTTP-fetched size in the stream/download selector
- upstream background/media-service playback retained
''', encoding="utf-8")

print("Extreme Tube v0.2 patch complete")

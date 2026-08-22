#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/build-extreme-tube.sh \
    <morphe-desktop-all.jar> \
    <official-morphe-patches.mpp> \
    <extreme-tube-patches.mpp> \
    <youtube.apk> \
    [output.apk]

The script performs no downloads. All inputs must already exist locally.
Set JAVA_BIN to an explicit Java 21 executable when needed.
EOF
}

if [[ $# -lt 4 || $# -gt 5 ]]; then
  usage >&2
  exit 2
fi

MORPHE_JAR=$1
MORPHE_PATCHES=$2
EXTREME_PATCHES=$3
YOUTUBE_APK=$4
OUTPUT=${5:-ExtremeTube-v0.1-refined.apk}
JAVA_BIN=${JAVA_BIN:-java}

for file in "$MORPHE_JAR" "$MORPHE_PATCHES" "$EXTREME_PATCHES" "$YOUTUBE_APK"; do
  if [[ ! -f "$file" ]]; then
    echo "Missing input file: $file" >&2
    exit 3
  fi
done

if [[ ! -x "$JAVA_BIN" ]] && ! command -v "$JAVA_BIN" >/dev/null 2>&1; then
  echo "Java executable not found: $JAVA_BIN" >&2
  exit 6
fi

case "$YOUTUBE_APK" in
  *.apk) ;;
  *)
    echo "Input must be a single .apk file for this locked-down build path." >&2
    exit 4
    ;;
esac

if [[ -e "$OUTPUT" ]]; then
  echo "Refusing to overwrite existing output: $OUTPUT" >&2
  exit 5
fi

printf 'Java runtime:\n'
"$JAVA_BIN" -version
printf '\nInput checksums:\n'
sha256sum "$MORPHE_JAR" "$MORPHE_PATCHES" "$EXTREME_PATCHES" "$YOUTUBE_APK"

# First-version Extreme Tube behavior, with only the requested refinements:
# - keep the YouTube 21.04.223 / Morphe-based UI and quality behavior
# - keep the package/branding from v0.1
# - enable Morphe's background playback restriction-removal patch
# - rename the injected settings entry to Extreme
# - remove the clickable Morphe About/social/update entry that can make Morphe web requests
# - preserve the mandatory offline Morphe GPL NOTICE/attribution
"$JAVA_BIN" -jar "$MORPHE_JAR" patch \
  -p "$MORPHE_PATCHES" \
    -e "Clone app" -O "packageName=com.extremetube.app" \
    -e "GmsCore support" \
    -e "Hide ads" \
    -e "Video quality" \
    -e "Remove background playback restrictions" \
  -p "$EXTREME_PATCHES" \
    -e "Extreme Tube branding" \
    -e "All Formats selector" \
    -e "Extreme settings cleanup" \
  --exclusive \
  --out "$OUTPUT" \
  "$YOUTUBE_APK"

printf '\nOutput checksum:\n'
sha256sum "$OUTPUT"
printf '\nCreated: %s\n' "$OUTPUT"

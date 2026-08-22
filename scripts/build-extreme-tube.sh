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
OUTPUT=${5:-ExtremeTube.apk}

for file in "$MORPHE_JAR" "$MORPHE_PATCHES" "$EXTREME_PATCHES" "$YOUTUBE_APK"; do
  if [[ ! -f "$file" ]]; then
    echo "Missing input file: $file" >&2
    exit 3
  fi
done

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

printf 'Input checksums:\n'
sha256sum "$MORPHE_JAR" "$MORPHE_PATCHES" "$EXTREME_PATCHES" "$YOUTUBE_APK"

# Security behavior:
# - --exclusive keeps the selected surface small and auditable.
# - patching stops on the first error (no --continue-on-error).
# - no network URL is supplied as a patch source.
# - package name is changed to a distinct Extreme Tube package.
# - GmsCore support pulls in its required stream-spoof/dependency patches.
# - Hide ads enables Morphe's YouTube ad-removal patch.
# - Video quality enables Morphe's current quality controller/fixes.
# - Extreme Tube branding changes the app label without adding network code.
# - All Formats selector exposes only formats already returned by YouTube.
# - Hide Morphe About removes only the About row from Morphe settings.
java -jar "$MORPHE_JAR" patch \
  -p "$MORPHE_PATCHES" \
    -e "Clone app" -O "packageName=com.extremetube.app" \
    -e "GmsCore support" \
    -e "Hide ads" \
    -e "Video quality" \
  -p "$EXTREME_PATCHES" \
    -e "Extreme Tube branding" \
    -e "All Formats selector" \
    -e "Hide Morphe About" \
  --exclusive \
  --out "$OUTPUT" \
  "$YOUTUBE_APK"

printf '\nOutput checksum:\n'
sha256sum "$OUTPUT"
printf '\nCreated: %s\n' "$OUTPUT"

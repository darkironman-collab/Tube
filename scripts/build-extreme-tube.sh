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
STAGE1="${OUTPUT%.apk}.stage1.apk"

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
  *) echo "Input must be a single .apk file." >&2; exit 4 ;;
esac

if [[ -e "$OUTPUT" || -e "$STAGE1" ]]; then
  echo "Refusing to overwrite existing output/stage file." >&2
  exit 5
fi

printf 'Java runtime:\n'
"$JAVA_BIN" -version
printf '\nInput checksums:\n'
sha256sum "$MORPHE_JAR" "$MORPHE_PATCHES" "$EXTREME_PATCHES" "$YOUTUBE_APK"

# PASS 1: let Morphe fully generate/serialize its settings and requested playback patches.
# GmsCore support depends on Clone app, so package cloning happens here as before.
"$JAVA_BIN" -jar "$MORPHE_JAR" patch \
  -p "$MORPHE_PATCHES" \
    -e "Clone app" -O "packageName=com.extremetube.app" \
    -e "GmsCore support" \
    -e "Hide ads" \
    -e "Video quality" \
    -e "Remove background playback restrictions" \
  --exclusive \
  --out "$STAGE1" \
  "$YOUTUBE_APK"

# PASS 2: work on the finalized cloned APK. Extreme patches are intentionally package-universal
# on this branch so they can modify the already-cloned com.extremetube.app artifact.
# In particular, morphe_prefs.xml now already contains the About row, allowing us to replace it
# reliably with a static Dark Ironman row and prevent the Morphe network About preference from opening.
"$JAVA_BIN" -jar "$MORPHE_JAR" patch \
  -p "$EXTREME_PATCHES" \
    -e "Extreme Tube branding" \
    -e "All Formats selector" \
    -e "Extreme settings cleanup" \
  --exclusive \
  --out "$OUTPUT" \
  "$STAGE1"

rm -f "$STAGE1"

printf '\nOutput checksum:\n'
sha256sum "$OUTPUT"
printf '\nCreated: %s\n' "$OUTPUT"

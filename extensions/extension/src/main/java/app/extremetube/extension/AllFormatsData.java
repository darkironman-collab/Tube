package app.extremetube.extension;

import com.google.protobuf.MessageLite;

import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import app.extremetube.extension.proto.FormatOuterClass.Format;

/**
 * In-process metadata and selection engine for the current video's adaptive formats.
 *
 * Security properties:
 * - performs no network I/O;
 * - never reads Android accounts, cookies, headers or credentials;
 * - never persists stream URLs or player responses;
 * - retains only in-memory references already owned by the running YouTube process.
 *
 * Extreme presets are preferences, not fake streams. A preset is mapped to the best matching
 * real adaptive format returned for the current video. If no match exists, normal Auto playback
 * is retained while the preset preference remains available for another video.
 */
@SuppressWarnings({"rawtypes", "unchecked", "unused"})
public final class AllFormatsData {
    private static volatile List<FormatInfo> latestFormats = Collections.emptyList();
    private static volatile List<Object> sourceFormatObjects = Collections.emptyList();
    private static volatile WeakReference<List<Object>> liveAdaptiveFormatsRef = new WeakReference<>(null);
    private static volatile int selectedItag = -1;
    private static volatile String selectedPresetId = null;

    private AllFormatsData() {
    }

    /**
     * Injection point called with the adaptive-format list already produced by YouTube.
     */
    public static synchronized List captureAndWrap(List adaptiveFormats) {
        if (adaptiveFormats == null || adaptiveFormats.isEmpty()) {
            latestFormats = Collections.emptyList();
            sourceFormatObjects = Collections.emptyList();
            liveAdaptiveFormatsRef = new WeakReference<>(null);
            selectedItag = -1;
            return adaptiveFormats == null ? new ArrayList<>() : new ArrayList<>(adaptiveFormats);
        }

        ArrayList<Object> mutable = new ArrayList<>(adaptiveFormats);
        liveAdaptiveFormatsRef = new WeakReference<>(mutable);
        sourceFormatObjects = Collections.unmodifiableList(new ArrayList<>(mutable));
        parseMetadata(sourceFormatObjects);

        // Presets intentionally persist in-memory across videos. The current video's real format
        // list is narrowed only if a compatible stream actually exists.
        ExtremeVideoPresets.Preset selectedPreset =
                ExtremeVideoPresets.findById(selectedPresetId);
        if (selectedPreset != null) {
            FormatInfo match = findBestForPreset(selectedPreset);
            if (match != null && filterLiveListToItag(match.getItag())) {
                selectedItag = match.getItag();
            } else {
                selectedItag = -1;
            }
        } else if (selectedItag > 0 && findFormat(selectedItag) != null) {
            filterLiveListToItag(selectedItag);
        } else {
            selectedItag = -1;
        }

        return mutable;
    }

    /** Backwards-compatible injection point used by older development builds. */
    public static void capture(List<?> adaptiveFormats) {
        captureAndWrap((List) adaptiveFormats);
    }

    public static List<FormatInfo> getLatestFormats() {
        return latestFormats;
    }

    public static int getSelectedItag() {
        return selectedItag;
    }

    public static String getSelectedPresetId() {
        return selectedPresetId;
    }

    public static boolean isPresetAvailable(ExtremeVideoPresets.Preset preset) {
        return preset != null && findBestForPreset(preset) != null;
    }

    public static FormatInfo getPresetMatch(ExtremeVideoPresets.Preset preset) {
        return preset == null ? null : findBestForPreset(preset);
    }

    /**
     * Select a SmartTube-style Extreme preset. The preference remains selected even when the
     * current video does not expose a matching stream; in that case playback falls back to Auto.
     */
    public static synchronized boolean selectPreset(String presetId) {
        ExtremeVideoPresets.Preset preset = ExtremeVideoPresets.findById(presetId);
        if (preset == null) return false;

        selectedPresetId = preset.getId();
        restoreLiveList();

        FormatInfo target = findBestForPreset(preset);
        if (target == null) {
            selectedItag = -1;
            changeNativeQuality(-2);
            return false;
        }

        if (!filterLiveListToItag(target.getItag())) {
            selectedItag = -1;
            changeNativeQuality(-2);
            return false;
        }

        selectedItag = target.getItag();
        if (changeNativeQuality(target.getHeight())) {
            return true;
        }

        // Never leave the current player narrowed if the native controller cannot refresh.
        restoreLiveList();
        selectedItag = -1;
        return false;
    }

    /** Select one exact current-video itag. Exact choices disable preset mode. */
    public static synchronized boolean selectItag(int itag) {
        selectedPresetId = null;
        return selectItagInternal(itag);
    }

    private static boolean selectItagInternal(int itag) {
        FormatInfo target = findFormat(itag);
        if (target == null || target.getHeight() <= 0) {
            return false;
        }

        restoreLiveList();
        if (!filterLiveListToItag(itag)) {
            return false;
        }

        selectedItag = itag;
        if (changeNativeQuality(target.getHeight())) {
            return true;
        }

        restoreLiveList();
        selectedItag = -1;
        return false;
    }

    /** Disable preset/exact mode and return to YouTube Auto. */
    public static synchronized boolean selectAutomatic() {
        restoreLiveList();
        selectedItag = -1;
        selectedPresetId = null;
        return changeNativeQuality(-2);
    }

    private static boolean filterLiveListToItag(int itag) {
        List<Object> live = liveAdaptiveFormatsRef.get();
        if (live == null || sourceFormatObjects.isEmpty()) {
            return false;
        }

        try {
            ArrayList<Object> filtered = new ArrayList<>(sourceFormatObjects.size());
            boolean selectedVideoFound = false;

            for (Object item : sourceFormatObjects) {
                if (!(item instanceof MessageLite)) {
                    filtered.add(item);
                    continue;
                }

                try {
                    Format format = Format.parseFrom(((MessageLite) item).toByteArray());
                    String mimeType = safe(format.getMimeType()).toLowerCase(Locale.ROOT);
                    if (!mimeType.startsWith("video/")) {
                        // Preserve every audio/non-video adaptive entry.
                        filtered.add(item);
                        continue;
                    }

                    if (format.getItag() == itag) {
                        filtered.add(item);
                        selectedVideoFound = true;
                    }
                } catch (Exception ignored) {
                    // Preserve opaque entries rather than risking playback breakage.
                    filtered.add(item);
                }
            }

            if (!selectedVideoFound) return false;

            live.clear();
            live.addAll(filtered);
            return true;
        } catch (Exception ignored) {
            restoreLiveList();
            return false;
        }
    }

    private static void restoreLiveList() {
        List<Object> live = liveAdaptiveFormatsRef.get();
        if (live == null || sourceFormatObjects.isEmpty()) return;

        try {
            live.clear();
            live.addAll(sourceFormatObjects);
        } catch (Exception ignored) {
            // Selection is optional. Playback must not crash if YouTube changes list semantics.
        }
    }

    /**
     * Uses Morphe's already-injected public VideoInformation bridge. Reflection keeps this
     * custom extension independent from Morphe's Java ABI at compile time.
     */
    private static boolean changeNativeQuality(int resolution) {
        try {
            Class<?> videoInformation = Class.forName(
                    "app.morphe.extension.youtube.patches.VideoInformation"
            );
            Class<?> qualityInterface = Class.forName(
                    "app.morphe.extension.youtube.patches.VideoInformation$VideoQualityInterface"
            );

            Method getCurrentQualities = videoInformation.getMethod("getCurrentQualities");
            Object qualities = getCurrentQualities.invoke(null);
            if (qualities == null || !qualities.getClass().isArray()) return false;

            Object chosen = null;
            Object premiumFallback = null;
            int length = Array.getLength(qualities);
            for (int i = 0; i < length; i++) {
                Object quality = Array.get(qualities, i);
                if (quality == null) continue;

                Method getResolution = quality.getClass().getMethod("patch_getResolution");
                int value = ((Number) getResolution.invoke(quality)).intValue();
                if (value != resolution) continue;

                boolean premium = false;
                try {
                    Method getName = quality.getClass().getMethod("patch_getQualityName");
                    Object name = getName.invoke(quality);
                    premium = name != null && name.toString().contains("Premium");
                } catch (Exception ignored) {
                    // Name is optional.
                }

                if (!premium) {
                    chosen = quality;
                    break;
                }
                premiumFallback = quality;
            }

            if (chosen == null) chosen = premiumFallback;
            if (chosen == null) return false;

            Method changeQuality = videoInformation.getMethod("changeQuality", qualityInterface);
            changeQuality.invoke(null, chosen);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void parseMetadata(List<?> adaptiveFormats) {
        try {
            ArrayList<FormatInfo> parsed = new ArrayList<>(adaptiveFormats.size());

            for (Object item : adaptiveFormats) {
                if (!(item instanceof MessageLite)) continue;

                try {
                    Format format = Format.parseFrom(((MessageLite) item).toByteArray());
                    String mimeType = safe(format.getMimeType());
                    if (!mimeType.toLowerCase(Locale.ROOT).startsWith("video/")) continue;

                    int bitrate = format.getAverageBitrate() > 0
                            ? format.getAverageBitrate()
                            : format.getBitrate();
                    String qualityLabel = safe(format.getQualityLabel());

                    parsed.add(new FormatInfo(
                            format.getItag(),
                            mimeType,
                            codecLabel(mimeType),
                            codecFamily(mimeType),
                            containerLabel(mimeType),
                            format.getWidth(),
                            format.getHeight(),
                            format.getFps(),
                            Math.max(0, bitrate),
                            Math.max(0L, format.getApproxDurationMs()),
                            detectHdr(mimeType, qualityLabel),
                            safe(format.getQuality()),
                            qualityLabel
                    ));
                } catch (Exception ignored) {
                    // A single unknown/changed format must never break the player.
                }
            }

            parsed.sort(
                    Comparator.comparingInt(FormatInfo::getHeight).reversed()
                            .thenComparing(Comparator.comparingInt(FormatInfo::getFps).reversed())
                            .thenComparing(FormatInfo::isHdr, Comparator.reverseOrder())
                            .thenComparing(Comparator.comparingInt(FormatInfo::getBitrate).reversed())
                            .thenComparingInt(FormatInfo::getItag)
            );

            latestFormats = Collections.unmodifiableList(parsed);
        } catch (Exception ignored) {
            latestFormats = Collections.emptyList();
        }
    }

    private static FormatInfo findFormat(int itag) {
        for (FormatInfo format : latestFormats) {
            if (format.getItag() == itag) return format;
        }
        return null;
    }

    private static FormatInfo findBestForPreset(ExtremeVideoPresets.Preset preset) {
        if (preset == null) return null;

        FormatInfo best = null;
        int bestScore = Integer.MIN_VALUE;

        for (FormatInfo format : latestFormats) {
            if (format.getHeight() != preset.getHeight()) continue;
            if (!preset.getCodec().equals(format.getCodecFamily())) continue;
            if (format.isHdr() != preset.isHdr()) continue;
            if (!fpsFitsPreset(format.getFps(), preset.getFps())) continue;

            int fps = format.getFps() <= 0 ? preset.getFps() : format.getFps();
            int fpsPenalty = Math.abs(preset.getFps() - fps) * 1000;
            int bitrateBonus = Math.min(900, format.getBitrate() / 100_000);
            int score = 100_000 - fpsPenalty + bitrateBonus;

            if (best == null || score > bestScore) {
                best = format;
                bestScore = score;
            }
        }

        return best;
    }

    private static boolean fpsFitsPreset(int actualFps, int presetFps) {
        if (actualFps <= 0) return true;
        if (presetFps >= 60) return actualFps > 30 && actualFps <= 60;
        return actualFps <= 30;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String codecLabel(String mimeType) {
        String family = codecFamily(mimeType);
        if (ExtremeVideoPresets.CODEC_AV1.equals(family)) return "AV1";
        if (ExtremeVideoPresets.CODEC_VP9.equals(family)) return "VP9";
        if (ExtremeVideoPresets.CODEC_AVC.equals(family)) return "AVC / H.264";

        String lower = mimeType.toLowerCase(Locale.ROOT);
        if (lower.contains("hev1") || lower.contains("hvc1") || lower.contains("hevc") || lower.contains("h265")) {
            return "HEVC / H.265";
        }

        int codecs = lower.indexOf("codecs=");
        if (codecs >= 0) {
            String value = mimeType.substring(codecs + "codecs=".length()).trim();
            value = value.replace("\"", "");
            int separator = value.indexOf(';');
            return separator >= 0 ? value.substring(0, separator).trim() : value;
        }
        return "Unknown";
    }

    private static String codecFamily(String mimeType) {
        String lower = safe(mimeType).toLowerCase(Locale.ROOT);
        if (lower.contains("av01")) return ExtremeVideoPresets.CODEC_AV1;
        if (lower.contains("vp09") || lower.contains("vp9")) return ExtremeVideoPresets.CODEC_VP9;
        if (lower.contains("avc1") || lower.contains("avc")) return ExtremeVideoPresets.CODEC_AVC;
        return "OTHER";
    }

    private static boolean detectHdr(String mimeType, String qualityLabel) {
        String mime = safe(mimeType).toLowerCase(Locale.ROOT);
        String label = safe(qualityLabel).toLowerCase(Locale.ROOT);
        if (label.contains("hdr")) return true;

        // YouTube VP9 HDR is profile 2. Common MIME forms include vp09.02 / vp9.2.
        if (mime.contains("vp09.02") || mime.contains("vp9.2")) return true;

        // AV1 codec strings expose bit depth (e.g. av01.0.08M.10...). YouTube's 10/12-bit AV1
        // renditions are treated as HDR candidates when no explicit HDR label is available.
        int av1 = mime.indexOf("av01.");
        if (av1 >= 0) {
            String tail = mime.substring(av1).replace("\"", "");
            int end = tail.indexOf(';');
            if (end >= 0) tail = tail.substring(0, end);
            String[] parts = tail.split("\\.");
            if (parts.length >= 4) {
                try {
                    int bitDepth = Integer.parseInt(parts[3].replaceAll("[^0-9]", ""));
                    return bitDepth >= 10;
                } catch (Exception ignored) {
                    // Fall through to SDR.
                }
            }
        }
        return false;
    }

    private static String containerLabel(String mimeType) {
        String lower = mimeType.toLowerCase(Locale.ROOT);
        if (lower.startsWith("video/webm")) return "WebM";
        if (lower.startsWith("video/mp4")) return "MP4";

        int slash = mimeType.indexOf('/');
        if (slash < 0 || slash + 1 >= mimeType.length()) return "Unknown";
        String container = mimeType.substring(slash + 1);
        int semicolon = container.indexOf(';');
        if (semicolon >= 0) container = container.substring(0, semicolon);
        container = container.trim();
        return container.isEmpty() ? "Unknown" : container;
    }

    public static final class FormatInfo {
        private final int itag;
        private final String mimeType;
        private final String codec;
        private final String codecFamily;
        private final String container;
        private final int width;
        private final int height;
        private final int fps;
        private final int bitrate;
        private final long durationMs;
        private final boolean hdr;
        private final String quality;
        private final String qualityLabel;

        private FormatInfo(
                int itag,
                String mimeType,
                String codec,
                String codecFamily,
                String container,
                int width,
                int height,
                int fps,
                int bitrate,
                long durationMs,
                boolean hdr,
                String quality,
                String qualityLabel
        ) {
            this.itag = itag;
            this.mimeType = mimeType;
            this.codec = codec;
            this.codecFamily = codecFamily;
            this.container = container;
            this.width = width;
            this.height = height;
            this.fps = fps;
            this.bitrate = bitrate;
            this.durationMs = durationMs;
            this.hdr = hdr;
            this.quality = quality;
            this.qualityLabel = qualityLabel;
        }

        public int getItag() { return itag; }
        public String getMimeType() { return mimeType; }
        public String getCodec() { return codec; }
        public String getCodecFamily() { return codecFamily; }
        public String getContainer() { return container; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public int getFps() { return fps; }
        public int getBitrate() { return bitrate; }
        public long getDurationMs() { return durationMs; }
        public boolean isHdr() { return hdr; }
        public String getQuality() { return quality; }
        public String getQualityLabel() { return qualityLabel; }

        public long getApproxSizeBytes() {
            if (bitrate <= 0 || durationMs <= 0) return 0L;
            return (bitrate * durationMs) / 8000L;
        }

        public String getDisplayLabel() {
            StringBuilder label = new StringBuilder();
            if (height > 0) {
                if (height >= 4320) label.append("(8K) ");
                else if (height >= 2160) label.append("(4K) ");
                else if (height >= 1440) label.append("(2K) ");
                label.append(height).append("p");
            } else if (!qualityLabel.isEmpty()) {
                label.append(qualityLabel);
            } else {
                label.append("Video");
            }

            if (fps > 0) label.append(" · ").append(fps).append("fps");
            label.append(" · ").append(codec);
            if (hdr) label.append(" HDR");
            if (!container.isEmpty() && !"Unknown".equals(container)) {
                label.append(" · ").append(container);
            }
            if (bitrate > 0) label.append(" · ").append(formatBitrate(bitrate));

            long bytes = getApproxSizeBytes();
            if (bytes > 0) label.append(" · ~").append(formatBytes(bytes));
            return label.toString();
        }

        private static String formatBitrate(int bitsPerSecond) {
            if (bitsPerSecond >= 1_000_000) {
                return String.format(Locale.ROOT, "%.1f Mbps", bitsPerSecond / 1_000_000.0);
            }
            if (bitsPerSecond >= 1_000) {
                return String.format(Locale.ROOT, "%.0f kbps", bitsPerSecond / 1_000.0);
            }
            return bitsPerSecond + " bps";
        }

        private static String formatBytes(long bytes) {
            double value = bytes;
            if (bytes >= 1024L * 1024L * 1024L) {
                return String.format(Locale.ROOT, "%.2f GB", value / (1024d * 1024d * 1024d));
            }
            if (bytes >= 1024L * 1024L) {
                return String.format(Locale.ROOT, "%.0f MB", value / (1024d * 1024d));
            }
            if (bytes >= 1024L) {
                return String.format(Locale.ROOT, "%.0f KB", value / 1024d);
            }
            return bytes + " B";
        }
    }
}

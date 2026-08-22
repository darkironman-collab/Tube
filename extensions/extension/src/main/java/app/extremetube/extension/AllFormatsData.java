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
 */
@SuppressWarnings({"rawtypes", "unchecked", "unused"})
public final class AllFormatsData {
    private static volatile List<FormatInfo> latestFormats = Collections.emptyList();
    private static volatile List<Object> sourceFormatObjects = Collections.emptyList();
    private static volatile WeakReference<List<Object>> liveAdaptiveFormatsRef = new WeakReference<>(null);
    private static volatile int selectedItag = -1;

    private AllFormatsData() {
    }

    /**
     * Injection point called with the adaptive-format list already produced by YouTube.
     *
     * A mutable wrapper is returned with identical contents. This gives Extreme Tube a safe,
     * in-process list that can later be narrowed to one exact video itag while preserving all
     * audio/unknown entries. No bytes are downloaded or persisted by this method.
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

        // If the user selected an exact format and a fresh streaming-data model is built,
        // keep that choice only when the same itag actually exists in the new model.
        if (selectedItag > 0 && findFormat(selectedItag) != null) {
            filterLiveListToItag(selectedItag);
        } else {
            selectedItag = -1;
        }

        return mutable;
    }

    /**
     * Backwards-compatible read-only injection point used by older development builds.
     */
    public static void capture(List<?> adaptiveFormats) {
        captureAndWrap((List) adaptiveFormats);
    }

    public static List<FormatInfo> getLatestFormats() {
        return latestFormats;
    }

    public static int getSelectedItag() {
        return selectedItag;
    }

    /**
     * Select one exact video itag and ask Morphe's already-injected native quality controller
     * to re-apply that resolution. All audio formats remain available.
     */
    public static synchronized boolean selectItag(int itag) {
        FormatInfo target = findFormat(itag);
        if (target == null || target.getHeight() <= 0) {
            return false;
        }

        if (!filterLiveListToItag(itag)) {
            return false;
        }

        selectedItag = itag;
        if (changeNativeQuality(target.getHeight())) {
            return true;
        }

        // Never leave playback narrowed if the native controller cannot be invoked.
        restoreLiveList();
        selectedItag = -1;
        return false;
    }

    /** Restore YouTube's full adaptive-format set and select the native Auto quality item. */
    public static synchronized boolean selectAutomatic() {
        restoreLiveList();
        selectedItag = -1;
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
                    // Unknown entries are preserved; the selector never deletes data it cannot identify.
                    filtered.add(item);
                    continue;
                }

                try {
                    Format format = Format.parseFrom(((MessageLite) item).toByteArray());
                    String mimeType = safe(format.getMimeType()).toLowerCase(Locale.ROOT);
                    if (!mimeType.startsWith("video/")) {
                        // Audio and non-video adaptive entries remain untouched.
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

            if (!selectedVideoFound) {
                return false;
            }

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
        if (live == null || sourceFormatObjects.isEmpty()) {
            return;
        }

        try {
            live.clear();
            live.addAll(sourceFormatObjects);
        } catch (Exception ignored) {
            // Selection is optional. Playback must not crash if YouTube changes list semantics.
        }
    }

    /**
     * Uses Morphe's public patched VideoInformation bridge already present in the final build.
     * Reflection keeps this custom extension independent from Morphe's internal Java ABI at compile time.
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
            if (qualities == null || !qualities.getClass().isArray()) {
                return false;
            }

            Object chosen = null;
            Object premiumFallback = null;
            int length = Array.getLength(qualities);
            for (int i = 0; i < length; i++) {
                Object quality = Array.get(qualities, i);
                if (quality == null) continue;

                Method getResolution = quality.getClass().getMethod("patch_getResolution");
                int value = ((Number) getResolution.invoke(quality)).intValue();
                if (value != resolution) continue;

                // Prefer the normal quality object over a Premium-labelled duplicate.
                boolean premium = false;
                try {
                    Method getName = quality.getClass().getMethod("patch_getQualityName");
                    Object name = getName.invoke(quality);
                    premium = name != null && name.toString().contains("Premium");
                } catch (Exception ignored) {
                    // Name is optional for our purposes.
                }

                if (!premium) {
                    chosen = quality;
                    break;
                }
                premiumFallback = quality;
            }

            if (chosen == null) chosen = premiumFallback;
            if (chosen == null) {
                return false;
            }

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
                if (!(item instanceof MessageLite)) {
                    continue;
                }

                try {
                    Format format = Format.parseFrom(((MessageLite) item).toByteArray());
                    String mimeType = safe(format.getMimeType());
                    if (!mimeType.toLowerCase(Locale.ROOT).startsWith("video/")) {
                        continue;
                    }

                    int bitrate = format.getAverageBitrate() > 0
                            ? format.getAverageBitrate()
                            : format.getBitrate();

                    parsed.add(new FormatInfo(
                            format.getItag(),
                            mimeType,
                            codecLabel(mimeType),
                            containerLabel(mimeType),
                            format.getWidth(),
                            format.getHeight(),
                            format.getFps(),
                            Math.max(0, bitrate),
                            safe(format.getQuality()),
                            safe(format.getQualityLabel())
                    ));
                } catch (Exception ignored) {
                    // A single unknown/changed format must never break the player.
                }
            }

            parsed.sort(
                    Comparator.comparingInt(FormatInfo::getHeight).reversed()
                            .thenComparing(Comparator.comparingInt(FormatInfo::getFps).reversed())
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

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String codecLabel(String mimeType) {
        String lower = mimeType.toLowerCase(Locale.ROOT);
        if (lower.contains("av01")) return "AV1";
        if (lower.contains("vp09") || lower.contains("vp9")) return "VP9";
        if (lower.contains("avc1") || lower.contains("avc")) return "AVC / H.264";
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
        private final String container;
        private final int width;
        private final int height;
        private final int fps;
        private final int bitrate;
        private final String quality;
        private final String qualityLabel;

        private FormatInfo(
                int itag,
                String mimeType,
                String codec,
                String container,
                int width,
                int height,
                int fps,
                int bitrate,
                String quality,
                String qualityLabel
        ) {
            this.itag = itag;
            this.mimeType = mimeType;
            this.codec = codec;
            this.container = container;
            this.width = width;
            this.height = height;
            this.fps = fps;
            this.bitrate = bitrate;
            this.quality = quality;
            this.qualityLabel = qualityLabel;
        }

        public int getItag() { return itag; }
        public String getMimeType() { return mimeType; }
        public String getCodec() { return codec; }
        public String getContainer() { return container; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public int getFps() { return fps; }
        public int getBitrate() { return bitrate; }
        public String getQuality() { return quality; }
        public String getQualityLabel() { return qualityLabel; }

        public String getDisplayLabel() {
            StringBuilder label = new StringBuilder();
            if (!qualityLabel.isEmpty()) {
                label.append(qualityLabel);
            } else if (height > 0) {
                label.append(height).append('p');
                if (fps > 30) label.append(fps);
            } else {
                label.append("Video");
            }

            if (height >= 4320) {
                label.append(" (8K)");
            } else if (height >= 2160) {
                label.append(" (4K)");
            }

            label.append(" · ").append(codec);
            if (!container.isEmpty() && !"Unknown".equals(container)) {
                label.append(" · ").append(container);
            }
            if (bitrate > 0) {
                label.append(" · ").append(formatBitrate(bitrate));
            }
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
    }
}

package app.extremetube.extension;

import com.google.protobuf.MessageLite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import app.extremetube.extension.proto.FormatOuterClass.Format;

/**
 * In-process metadata cache for the current video's adaptive formats.
 *
 * This class performs no network I/O, does not access Android accounts, and does not retain
 * stream URLs, cookies, authentication headers or other credentials.
 */
@SuppressWarnings("unused")
public final class AllFormatsData {
    private static volatile List<FormatInfo> latestFormats = Collections.emptyList();

    private AllFormatsData() {
    }

    /**
     * Injection point called with the adaptive format list already produced by YouTube.
     * Any unexpected protobuf value is ignored rather than affecting playback.
     */
    public static void capture(List<?> adaptiveFormats) {
        if (adaptiveFormats == null || adaptiveFormats.isEmpty()) {
            latestFormats = Collections.emptyList();
            return;
        }

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
            // Fail closed: metadata enhancement is optional, playback is not.
            latestFormats = Collections.emptyList();
        }
    }

    public static List<FormatInfo> getLatestFormats() {
        return latestFormats;
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
            } else {
                label.append("Video");
            }

            if (fps > 30 && !label.toString().contains(String.valueOf(fps))) {
                label.append(fps);
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

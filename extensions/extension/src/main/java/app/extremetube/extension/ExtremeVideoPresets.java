package app.extremetube.extension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Extreme Tube's own video-preset catalog.
 *
 * The capability matrix was independently implemented after studying the public SmartTube
 * project (MIT licensed, copyright 2020-present yuliskov). No SmartTube UI code is copied.
 * Presets are preferences only: they never synthesize a stream that YouTube did not return.
 */
@SuppressWarnings("unused")
public final class ExtremeVideoPresets {
    public static final String CODEC_AV1 = "AV1";
    public static final String CODEC_VP9 = "VP9";
    public static final String CODEC_AVC = "AVC";

    private static final List<Preset> PRESETS = buildPresets();

    private ExtremeVideoPresets() {
    }

    public static List<Preset> getAll() {
        return PRESETS;
    }

    public static Preset findById(String id) {
        if (id == null) return null;
        for (Preset preset : PRESETS) {
            if (id.equals(preset.id)) return preset;
        }
        return null;
    }

    private static List<Preset> buildPresets() {
        ArrayList<Preset> result = new ArrayList<>();

        // High-resolution profiles. Order intentionally follows the compact SmartTube-style
        // browsing pattern: HDR first, then SDR, 60 fps before 30 fps.
        addResolution(result, 4320, true, false);
        addResolution(result, 2160, true, false);
        addResolution(result, 1440, true, false);
        addResolution(result, 1080, true, true);
        addResolution(result, 720, true, true);
        addResolution(result, 480, true, true);
        addResolution(result, 360, true, true);

        // Very low resolutions are normally 30 fps only.
        addLowResolution(result, 240);
        addLowResolution(result, 144);

        return Collections.unmodifiableList(result);
    }

    private static void addResolution(
            List<Preset> result,
            int height,
            boolean include60,
            boolean includeAvc
    ) {
        int[] frameRates = include60 ? new int[]{60, 30} : new int[]{30};

        for (int fps : frameRates) result.add(new Preset(height, fps, CODEC_AV1, true));
        for (int fps : frameRates) result.add(new Preset(height, fps, CODEC_AV1, false));
        for (int fps : frameRates) result.add(new Preset(height, fps, CODEC_VP9, true));
        for (int fps : frameRates) result.add(new Preset(height, fps, CODEC_VP9, false));
        if (includeAvc) {
            for (int fps : frameRates) result.add(new Preset(height, fps, CODEC_AVC, false));
        }
    }

    private static void addLowResolution(List<Preset> result, int height) {
        result.add(new Preset(height, 30, CODEC_AV1, false));
        result.add(new Preset(height, 30, CODEC_VP9, false));
        result.add(new Preset(height, 30, CODEC_AVC, false));
    }

    public static final class Preset {
        private final String id;
        private final int height;
        private final int fps;
        private final String codec;
        private final boolean hdr;

        private Preset(int height, int fps, String codec, boolean hdr) {
            this.height = height;
            this.fps = fps;
            this.codec = codec;
            this.hdr = hdr;
            this.id = height + ":" + fps + ":" + codec.toLowerCase(Locale.ROOT) + ":" + hdr;
        }

        public String getId() { return id; }
        public int getHeight() { return height; }
        public int getFps() { return fps; }
        public String getCodec() { return codec; }
        public boolean isHdr() { return hdr; }

        public String getDisplayName() {
            StringBuilder label = new StringBuilder();
            if (height >= 4320) {
                label.append("(8K) ");
            } else if (height >= 2160) {
                label.append("(4K) ");
            } else if (height >= 1440) {
                label.append("(2K) ");
            }
            label.append(height).append("p  ").append(fps).append("fps  ");
            if (CODEC_AV1.equals(codec)) {
                label.append("av01");
            } else if (CODEC_VP9.equals(codec)) {
                label.append("vp9");
            } else {
                label.append("avc");
            }
            if (hdr) label.append("+hdr");
            return label.toString();
        }
    }
}

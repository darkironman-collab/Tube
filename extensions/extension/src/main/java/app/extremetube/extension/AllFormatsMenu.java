package app.extremetube.extension;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Extreme Tube quality selector.
 *
 * The surrounding sheet is YouTube's existing quality UI. Inside it we expose:
 *  1) a SmartTube-style preset catalog (8K/4K/2K/1080p/etc, FPS, codec, HDR), and
 *  2) every real adaptive format returned for the current video.
 *
 * Presets never invent streams. Unavailable presets stay visible so the user can keep a preferred
 * profile; playback falls back to Auto until a matching real stream exists.
 */
@SuppressWarnings("unused")
public final class AllFormatsMenu {
    private static final int MAX_INSTALL_ATTEMPTS = 12;
    private static final int PRESET_HEADER = 0;
    private static final int AUTO_ROW = 1;
    private static final Set<ListView> INSTALLED =
            Collections.newSetFromMap(new WeakHashMap<>());

    private AllFormatsMenu() {
    }

    /** Injection point called after YouTube creates the advanced quality ListView. */
    public static void install(ListView listView) {
        if (listView == null) return;
        listView.post(() -> installWhenReady(listView, 0));
    }

    private static void installWhenReady(ListView listView, int attempt) {
        try {
            List<AllFormatsData.FormatInfo> formats = AllFormatsData.getLatestFormats();
            if (formats == null) formats = Collections.emptyList();

            // IMPORTANT: presets are useful even before YouTube's adaptive-format metadata has
            // been captured. Always install the preset UI immediately, then refresh the same
            // adapter in the background when real formats arrive.
            if (formats.isEmpty() && attempt < MAX_INSTALL_ATTEMPTS) {
                final int nextAttempt = attempt + 1;
                listView.postDelayed(() -> installWhenReady(listView, nextAttempt), 150L);
            }

            synchronized (INSTALLED) {
                if (INSTALLED.contains(listView) && listView.getAdapter() instanceof AllFormatsAdapter) {
                    ((AllFormatsAdapter) listView.getAdapter()).replaceFormats(formats);
                    return;
                }
                INSTALLED.add(listView);
            }

            AllFormatsAdapter adapter = new AllFormatsAdapter(listView.getContext(), formats);
            listView.setAdapter(adapter);
            listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            listView.setOnItemClickListener((parent, view, position, id) -> {
                if (!adapter.isSelectable(position)) return;

                if (position == AUTO_ROW) {
                    boolean success = AllFormatsData.selectAutomatic();
                    adapter.notifyDataSetChanged();
                    Toast.makeText(
                            listView.getContext(),
                            success ? "Video presets disabled • YouTube Auto" : "Preset mode disabled",
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

                ExtremeVideoPresets.Preset preset = adapter.getPresetAtPosition(position);
                if (preset != null) {
                    boolean applied = AllFormatsData.selectPreset(preset.getId());
                    adapter.notifyDataSetChanged();
                    Toast.makeText(
                            listView.getContext(),
                            applied
                                    ? "Applied • " + preset.getDisplayName()
                                    : "Preset saved • not available in this video",
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

                AllFormatsData.FormatInfo format = adapter.getFormatAtPosition(position);
                if (format != null) {
                    boolean success = AllFormatsData.selectItag(format.getItag());
                    adapter.notifyDataSetChanged();
                    Toast.makeText(
                            listView.getContext(),
                            success ? format.getDisplayLabel() : "Format switch unavailable",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });
        } catch (Throwable ignored) {
            // UI enhancement must never make the YouTube quality sheet crash.
        }
    }

    private static final class AllFormatsAdapter extends BaseAdapter {
        private final Context context;
        private final List<ExtremeVideoPresets.Preset> presets = ExtremeVideoPresets.getAll();
        private List<AllFormatsData.FormatInfo> formats;
        private final int textColor;
        private final int secondaryTextColor;
        private final int rowHeight;
        private final int headerHeight;
        private final int horizontalPadding;
        private final int selectableBackground;

        AllFormatsAdapter(Context context, List<AllFormatsData.FormatInfo> formats) {
            this.context = context;
            this.formats = formats == null ? Collections.emptyList() : formats;
            this.rowHeight = dp(context, 52);
            this.headerHeight = dp(context, 42);
            this.horizontalPadding = dp(context, 24);
            this.textColor = resolveTextColor(context);
            this.secondaryTextColor = resolveSecondaryTextColor(context, textColor);
            this.selectableBackground = resolveSelectableBackground(context);
        }

        void replaceFormats(List<AllFormatsData.FormatInfo> newFormats) {
            this.formats = newFormats == null ? Collections.emptyList() : newFormats;
            notifyDataSetChanged();
        }

        private int actualHeaderPosition() {
            return AUTO_ROW + 1 + presets.size();
        }

        private int firstActualFormatPosition() {
            return actualHeaderPosition() + 1;
        }

        boolean isSelectable(int position) {
            return position != PRESET_HEADER && position != actualHeaderPosition();
        }

        ExtremeVideoPresets.Preset getPresetAtPosition(int position) {
            int index = position - (AUTO_ROW + 1);
            return index >= 0 && index < presets.size() ? presets.get(index) : null;
        }

        AllFormatsData.FormatInfo getFormatAtPosition(int position) {
            int index = position - firstActualFormatPosition();
            return index >= 0 && index < formats.size() ? formats.get(index) : null;
        }

        @Override
        public int getCount() {
            // Video Presets header + Disabled/Auto + preset rows + Actual Formats header + formats.
            return 1 + 1 + presets.size() + 1 + formats.size();
        }

        @Override
        public Object getItem(int position) {
            ExtremeVideoPresets.Preset preset = getPresetAtPosition(position);
            if (preset != null) return preset;
            return getFormatAtPosition(position);
        }

        @Override
        public long getItemId(int position) {
            ExtremeVideoPresets.Preset preset = getPresetAtPosition(position);
            if (preset != null) return -100_000L - position;
            AllFormatsData.FormatInfo format = getFormatAtPosition(position);
            return format == null ? -position - 1L : format.getItag();
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return isSelectable(position);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView row;
            if (convertView instanceof TextView) {
                row = (TextView) convertView;
            } else {
                row = new TextView(context);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(horizontalPadding, 0, horizontalPadding, 0);
                row.setMaxLines(2);
            }

            // Reset recycled state first.
            row.setAlpha(1.0f);
            row.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
            row.setTextColor(textColor);
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
            row.setMinHeight(rowHeight);
            if (selectableBackground != 0) {
                row.setBackgroundResource(selectableBackground);
            } else {
                row.setBackgroundColor(Color.TRANSPARENT);
            }

            if (position == PRESET_HEADER) {
                styleHeader(row, "VIDEO PRESETS");
                return row;
            }
            if (position == actualHeaderPosition()) {
                styleHeader(row, "ACTUAL FORMATS FOR THIS VIDEO");
                return row;
            }

            String selectedPresetId = AllFormatsData.getSelectedPresetId();
            int selectedItag = AllFormatsData.getSelectedItag();

            if (position == AUTO_ROW) {
                boolean selected = selectedPresetId == null && selectedItag < 0;
                row.setText(radio(selected) + "Disabled (YouTube Auto)");
                return row;
            }

            ExtremeVideoPresets.Preset preset = getPresetAtPosition(position);
            if (preset != null) {
                boolean selected = preset.getId().equals(selectedPresetId);
                boolean available = AllFormatsData.isPresetAvailable(preset);
                row.setText(radio(selected) + preset.getDisplayName());
                // Keep every preset visible. A softer row means this exact profile is absent only
                // for the current video; tapping still stores it as the preferred preset.
                if (!available && !selected) row.setAlpha(0.50f);
                return row;
            }

            AllFormatsData.FormatInfo format = getFormatAtPosition(position);
            if (format == null) {
                row.setText("");
                return row;
            }

            boolean selected = selectedPresetId == null && format.getItag() == selectedItag;
            row.setText(radio(selected) + format.getDisplayLabel());
            return row;
        }

        private void styleHeader(TextView row, String text) {
            row.setText(text);
            row.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            row.setTextColor(secondaryTextColor);
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            row.setMinHeight(headerHeight);
            row.setAlpha(0.85f);
            row.setBackgroundColor(Color.TRANSPARENT);
        }

        private static String radio(boolean selected) {
            return selected ? "◉  " : "○  ";
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static int resolveTextColor(Context context) {
        TypedArray values = context.obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
        try {
            return values.getColor(0, Color.WHITE);
        } finally {
            values.recycle();
        }
    }

    private static int resolveSecondaryTextColor(Context context, int fallback) {
        TypedArray values = context.obtainStyledAttributes(new int[]{android.R.attr.textColorSecondary});
        try {
            return values.getColor(0, fallback);
        } finally {
            values.recycle();
        }
    }

    private static int resolveSelectableBackground(Context context) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true)) {
            return value.resourceId;
        }
        return 0;
    }
}

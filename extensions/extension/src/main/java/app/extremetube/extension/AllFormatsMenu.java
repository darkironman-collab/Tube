package app.extremetube.extension;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
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
 * Replaces the contents of YouTube's existing advanced-quality ListView with one row per actual
 * adaptive video format. The surrounding YouTube bottom sheet remains the original YouTube UI.
 */
@SuppressWarnings("unused")
public final class AllFormatsMenu {
    private static final int MAX_INSTALL_ATTEMPTS = 6;
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
            if ((formats == null || formats.isEmpty()) && attempt < MAX_INSTALL_ATTEMPTS) {
                listView.postDelayed(() -> installWhenReady(listView, attempt + 1), 120L);
                return;
            }
            if (formats == null || formats.isEmpty()) {
                // Fail open: keep YouTube's original quality UI when stream metadata is unavailable.
                return;
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
                boolean success;
                String message;

                if (position == 0) {
                    success = AllFormatsData.selectAutomatic();
                    message = success ? "Auto (recommended)" : "Could not switch to Auto";
                } else {
                    AllFormatsData.FormatInfo format = adapter.getFormat(position - 1);
                    if (format == null) return;
                    success = AllFormatsData.selectItag(format.getItag());
                    message = success ? format.getDisplayLabel() : "Format switch unavailable";
                }

                adapter.notifyDataSetChanged();
                Toast.makeText(listView.getContext(), message, Toast.LENGTH_SHORT).show();
            });
        } catch (Throwable ignored) {
            // UI enhancement must never make the YouTube quality sheet crash.
        }
    }

    private static final class AllFormatsAdapter extends BaseAdapter {
        private final Context context;
        private List<AllFormatsData.FormatInfo> formats;
        private final int textColor;
        private final int rowHeight;
        private final int horizontalPadding;
        private final int selectableBackground;

        AllFormatsAdapter(Context context, List<AllFormatsData.FormatInfo> formats) {
            this.context = context;
            this.formats = formats;
            this.rowHeight = dp(context, 52);
            this.horizontalPadding = dp(context, 24);
            this.textColor = resolveTextColor(context);
            this.selectableBackground = resolveSelectableBackground(context);
        }

        void replaceFormats(List<AllFormatsData.FormatInfo> newFormats) {
            this.formats = newFormats;
            notifyDataSetChanged();
        }

        AllFormatsData.FormatInfo getFormat(int index) {
            return index >= 0 && index < formats.size() ? formats.get(index) : null;
        }

        @Override
        public int getCount() {
            // First row is native-style Auto, followed by every concrete format.
            return formats.size() + 1;
        }

        @Override
        public Object getItem(int position) {
            return position == 0 ? null : getFormat(position - 1);
        }

        @Override
        public long getItemId(int position) {
            if (position == 0) return -2;
            AllFormatsData.FormatInfo format = getFormat(position - 1);
            return format == null ? position : format.getItag();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView row;
            if (convertView instanceof TextView) {
                row = (TextView) convertView;
            } else {
                row = new TextView(context);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinHeight(rowHeight);
                row.setPadding(horizontalPadding, 0, horizontalPadding, 0);
                row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
                row.setTextColor(textColor);
                row.setMaxLines(2);
                if (selectableBackground != 0) {
                    row.setBackgroundResource(selectableBackground);
                }
            }

            int selectedItag = AllFormatsData.getSelectedItag();
            if (position == 0) {
                row.setText(selectedItag < 0 ? "✓  Auto (recommended)" : "    Auto (recommended)");
            } else {
                AllFormatsData.FormatInfo format = getFormat(position - 1);
                if (format == null) {
                    row.setText("");
                } else {
                    String prefix = format.getItag() == selectedItag ? "✓  " : "    ";
                    row.setText(prefix + format.getDisplayLabel());
                }
            }
            return row;
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

    private static int resolveSelectableBackground(Context context) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true)) {
            return value.resourceId;
        }
        return 0;
    }
}

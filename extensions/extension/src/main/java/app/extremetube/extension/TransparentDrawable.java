package app.extremetube.extension;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** Invisible drawable used to remove the Morphe M icon beside the Extreme settings entry. */
@SuppressWarnings({"unused", "deprecation"})
public final class TransparentDrawable extends Drawable {
    @Override
    public void draw(Canvas canvas) {
        // Deliberately empty.
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return 1;
    }

    @Override
    public int getIntrinsicHeight() {
        return 1;
    }
}

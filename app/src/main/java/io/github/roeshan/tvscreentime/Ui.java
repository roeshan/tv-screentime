package io.github.roeshan.tvscreentime;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

final class Ui {
    static final int TEXT = 0xFFFFFFFF;
    static final int DIM = 0xB3FFFFFF;
    static final int ERROR = 0xFFFF7A7A;
    static final int CELL = 0x26FFFFFF;
    static final int FOCUS = 0xFFFFFFFF;
    static final int ON_FOCUS = 0xFF000000;
    static final int SCRIM = 0xF5000000;

    private Ui() {}

    /** The PIN pad and menus sit in a fixed-width column centred on screen. */
    static FrameLayout.LayoutParams centered(Context c) {
        return new FrameLayout.LayoutParams(dp(c, 600), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context c, float sp, int color) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    static GradientDrawable round(Context c, int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, 14));
        return d;
    }

    static void focus(Context c, TextView t, boolean focused) {
        t.setBackground(round(c, focused ? FOCUS : CELL));
        t.setTextColor(focused ? ON_FOCUS : TEXT);
    }
}

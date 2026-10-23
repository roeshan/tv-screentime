package io.github.roeshan.tvscreentime;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;
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

    /**
     * Puts content in a column centred on screen: 600dp wide on a TV or tablet, the screen width less a margin on a
     * phone, and scrollable where the screen is too short for it (a phone held sideways).
     */
    static View column(Context c, View content) {
        if (content.getParent() instanceof ViewGroup) ((ViewGroup) content.getParent()).removeView(content);
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        int width = Math.min(dp(c, 600), dm.widthPixels - dp(c, 32));

        FrameLayout centre = new FrameLayout(c);
        int pad = dp(c, 16);
        centre.setPadding(0, pad, 0, pad);
        centre.addView(content, new FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        ScrollView scroll = new ScrollView(c);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(centre, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        return scroll;
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

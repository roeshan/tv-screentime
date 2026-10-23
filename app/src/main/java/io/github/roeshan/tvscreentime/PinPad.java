package io.github.roeshan.tvscreentime;

import android.content.Context;
import android.view.KeyEvent;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A 4-digit PIN entry driven entirely by key codes, so it works both inside an activity and in an
 * accessibility overlay that never takes focus. D-pad moves over the keypad; number keys type directly.
 */
final class PinPad {
    interface Listener { void onPin(String pin); }

    private static final String[] KEYS = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK"};
    private static final int COLS = 3;

    final LinearLayout root;
    private final Context c;
    private final Listener listener;
    private final TextView title, subtitle, dots;
    private final TextView[] cells = new TextView[KEYS.length];
    private final StringBuilder pin = new StringBuilder();
    private int focus = 4;

    PinPad(Context c, Listener listener) {
        this.c = c;
        this.listener = listener;
        root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(android.view.Gravity.CENTER_HORIZONTAL);

        title = Ui.text(c, 28, Ui.TEXT);
        subtitle = Ui.text(c, 16, Ui.DIM);
        dots = Ui.text(c, 34, Ui.TEXT);
        root.addView(title);
        root.addView(subtitle);
        LinearLayout.LayoutParams dotsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dotsLp.topMargin = Ui.dp(c, 10);
        dotsLp.bottomMargin = Ui.dp(c, 10);
        root.addView(dots, dotsLp);

        GridLayout grid = new GridLayout(c);
        grid.setColumnCount(COLS);
        int m = Ui.dp(c, 5);
        for (int i = 0; i < KEYS.length; i++) {
            TextView t = Ui.text(c, 24, Ui.TEXT);
            t.setText(KEYS[i]);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = Ui.dp(c, 92);
            lp.height = Ui.dp(c, 58);
            lp.setMargins(m, m, m, m);
            grid.addView(t, lp);
            cells[i] = t;
        }
        root.addView(grid, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        render();
    }

    void setTitle(String t, String sub) {
        title.setText(t);
        subtitle.setText(sub);
        subtitle.setTextColor(Ui.DIM);
    }

    void reset() {
        pin.setLength(0);
        focus = 4;
        render();
    }

    void error(String msg) {
        subtitle.setText(msg);
        subtitle.setTextColor(Ui.ERROR);
        pin.setLength(0);
        render();
    }

    boolean onKey(int code) {
        int row = focus / COLS, col = focus % COLS, rows = KEYS.length / COLS;
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
            digit(String.valueOf(code - KeyEvent.KEYCODE_0));
        } else if (code >= KeyEvent.KEYCODE_NUMPAD_0 && code <= KeyEvent.KEYCODE_NUMPAD_9) {
            digit(String.valueOf(code - KeyEvent.KEYCODE_NUMPAD_0));
        } else {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT: focus = row * COLS + (col + COLS - 1) % COLS; break;
                case KeyEvent.KEYCODE_DPAD_RIGHT: focus = row * COLS + (col + 1) % COLS; break;
                case KeyEvent.KEYCODE_DPAD_UP: focus = ((row + rows - 1) % rows) * COLS + col; break;
                case KeyEvent.KEYCODE_DPAD_DOWN: focus = ((row + 1) % rows) * COLS + col; break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER: press(KEYS[focus]); break;
                case KeyEvent.KEYCODE_DEL: backspace(); break;
                default: return false;
            }
        }
        render();
        return true;
    }

    private void press(String key) {
        if ("⌫".equals(key)) backspace();
        else if ("OK".equals(key)) submit();
        else digit(key);
    }

    private void digit(String d) {
        if (pin.length() < Store.PIN_LENGTH) pin.append(d);
        if (pin.length() == Store.PIN_LENGTH) submit();
    }

    private void backspace() {
        if (pin.length() > 0) pin.setLength(pin.length() - 1);
    }

    private void submit() {
        if (pin.length() < Store.PIN_LENGTH) return;
        String entered = pin.toString();
        pin.setLength(0);
        render();
        listener.onPin(entered);
    }

    private void render() {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < Store.PIN_LENGTH; i++) {
            if (i > 0) s.append("  ");
            s.append(i < pin.length() ? "●" : "○");
        }
        dots.setText(s);
        for (int i = 0; i < cells.length; i++) Ui.focus(c, cells[i], i == focus);
    }
}

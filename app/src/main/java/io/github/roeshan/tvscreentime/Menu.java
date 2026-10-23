package io.github.roeshan.tvscreentime;

import android.content.Context;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** A vertical list of choices driven by key codes; LEFT/RIGHT adjust values on items that support it. */
final class Menu {
    static final class Item {
        final Supplier<String> label;
        final Runnable ok, left, right;

        Item(Supplier<String> label, Runnable ok, Runnable left, Runnable right) {
            this.label = label;
            this.ok = ok;
            this.left = left;
            this.right = right;
        }
    }

    static Item item(String label, Runnable ok) {
        return new Item(() -> label, ok, null, null);
    }

    final LinearLayout root;
    private final Context c;
    private final TextView title, subtitle;
    private final List<TextView> rows = new ArrayList<>();
    private List<Item> items = new ArrayList<>();
    private Supplier<String> sub = () -> "";
    private int sel;

    Menu(Context c) {
        this.c = c;
        root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        title = Ui.text(c, 28, Ui.TEXT);
        subtitle = Ui.text(c, 16, Ui.DIM);
        root.addView(title);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(c, 14);
        root.addView(subtitle, lp);
    }

    void set(String t, Supplier<String> subtitleText, List<Item> list) {
        title.setText(t);
        sub = subtitleText;
        for (TextView r : rows) root.removeView(r);
        rows.clear();
        items = list;
        int pad = Ui.dp(c, 10);
        for (int i = 0; i < list.size(); i++) {
            TextView r = Ui.text(c, 20, Ui.TEXT);
            r.setPadding(pad * 2, pad, pad * 2, pad);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 460),
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(c, 6);
            root.addView(r, lp);
            rows.add(r);
        }
        sel = 0;
        refresh();
    }

    void refresh() {
        subtitle.setText(sub.get());
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setText(items.get(i).label.get());
            Ui.focus(c, rows.get(i), i == sel);
        }
    }

    boolean onKey(int code) {
        int n = items.size();
        if (n == 0) return false;
        Runnable action = null;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: sel = (sel + n - 1) % n; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: sel = (sel + 1) % n; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: action = items.get(sel).left; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: action = items.get(sel).right; break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER: action = items.get(sel).ok; break;
            default: return false;
        }
        if (action != null) action.run();
        refresh();
        return true;
    }
}

package io.github.roeshan.tvscreentime;

import android.content.Context;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A vertical list of choices for a remote and a touchscreen. Keys: UP/DOWN move, OK picks, LEFT/RIGHT adjust items
 * that have a value. Touch: tap a row to pick it, tap ◀ or ▶ to adjust.
 */
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

        boolean adjustable() { return left != null || right != null; }
    }

    static Item item(String label, Runnable ok) {
        return new Item(() -> label, ok, null, null);
    }

    /** One row: its background, its label, and the ◀ ▶ buttons of an adjustable item. */
    private static final class Row {
        final LinearLayout view;
        final TextView label, less, more;

        Row(LinearLayout view, TextView label, TextView less, TextView more) {
            this.view = view;
            this.label = label;
            this.less = less;
            this.more = more;
        }
    }

    final LinearLayout root;
    private final Context c;
    private final TextView title, subtitle;
    private final List<Row> rows = new ArrayList<>();
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
        for (Row r : rows) root.removeView(r.view);
        rows.clear();
        items = list;
        int pad = Ui.dp(c, 10);
        for (int i = 0; i < list.size(); i++) {
            Item item = list.get(i);
            int index = i;
            LinearLayout view = new LinearLayout(c);
            view.setOrientation(LinearLayout.HORIZONTAL);
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setPadding(pad, pad, pad, pad);
            view.setOnClickListener(v -> run(index, item.ok));

            TextView label = Ui.text(c, 20, Ui.TEXT);
            TextView less = null, more = null;
            if (item.adjustable()) {
                less = arrow("◀", () -> run(index, item.left));
                view.addView(less);
            }
            view.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            if (item.adjustable()) {
                more = arrow("▶", () -> run(index, item.right));
                view.addView(more);
            }

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(c, 6);
            root.addView(view, lp);
            rows.add(new Row(view, label, less, more));
        }
        sel = 0;
        refresh();
    }

    private TextView arrow(String glyph, Runnable action) {
        TextView t = Ui.text(c, 20, Ui.TEXT);
        t.setText(glyph);
        int p = Ui.dp(c, 12);
        t.setPadding(p * 2, p / 2, p * 2, p / 2);
        t.setOnClickListener(v -> action.run());
        return t;
    }

    private void run(int index, Runnable action) {
        sel = index;
        if (action != null) action.run();
        refresh();
    }

    void refresh() {
        subtitle.setText(sub.get());
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            boolean focused = i == sel;
            int color = focused ? Ui.ON_FOCUS : Ui.TEXT;
            r.view.setBackground(Ui.round(c, focused ? Ui.FOCUS : Ui.CELL));
            r.label.setText(items.get(i).label.get());
            r.label.setTextColor(color);
            if (r.less != null) r.less.setTextColor(color);
            if (r.more != null) r.more.setTextColor(color);
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

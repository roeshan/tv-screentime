package io.github.roeshan.tvscreentime;

import android.app.Activity;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import java.util.List;

/** Parent screen: create the PIN on first run, then set quotas and today's state behind that PIN. */
public class SetupActivity extends Activity {

    private enum Stage { VERIFY, CREATE, CONFIRM, SETTINGS }

    private Store store;
    private FrameLayout frame;
    private PinPad pad;
    private Menu menu;
    private Stage stage;
    private String firstPin;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = new Store(this);
        store.rollDay();
        frame = new FrameLayout(this);
        frame.setBackgroundColor(0xFF000000);
        setContentView(frame);
        pad = new PinPad(this, this::onPin);
        menu = new Menu(this);
        start(store.hasPin() ? Stage.VERIFY : Stage.CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (stage == Stage.SETTINGS) menu.refresh();
    }

    private void start(Stage s) {
        stage = s;
        View content;
        switch (s) {
            case VERIFY:
                pad.reset();
                pad.setTitle(getString(R.string.app_name), getString(R.string.verify_sub));
                content = pad.root;
                break;
            case CREATE:
                pad.reset();
                pad.setTitle(getString(R.string.create_title), getString(R.string.create_sub));
                content = pad.root;
                break;
            case CONFIRM:
                pad.reset();
                pad.setTitle(getString(R.string.confirm_title), getString(R.string.confirm_sub));
                content = pad.root;
                break;
            default:
                buildSettings();
                content = menu.root;
                break;
        }
        frame.removeAllViews();
        frame.addView(Ui.column(this, content));
    }

    private void onPin(String pin) {
        switch (stage) {
            case VERIFY: {
                long wait = store.lockoutLeftMs();
                if (wait > 0) pad.error(getString(R.string.pin_lockout, (int) (wait / 1000 + 1)));
                else if (store.checkPin(pin)) start(Stage.SETTINGS);
                else pad.error(getString(R.string.pin_wrong));
                break;
            }
            case CREATE:
                firstPin = pin;
                start(Stage.CONFIRM);
                break;
            case CONFIRM:
                if (pin.equals(firstPin)) {
                    store.setPin(pin);
                    start(Stage.SETTINGS);
                } else {
                    start(Stage.CREATE);
                    pad.error(getString(R.string.pin_mismatch));
                }
                break;
            default:
                break;
        }
    }

    private void buildSettings() {
        menu.set(getString(R.string.app_name), this::status, List.of(
                new Menu.Item(() -> getString(R.string.weekday_quota, store.weekdayMin()), null,
                        () -> store.setWeekdayMin(Math.max(5, store.weekdayMin() - 5)),
                        () -> store.setWeekdayMin(Math.min(600, store.weekdayMin() + 5))),
                new Menu.Item(() -> getString(R.string.weekend_quota, store.weekendMin()), null,
                        () -> store.setWeekendMin(Math.max(15, store.weekendMin() - 15)),
                        () -> store.setWeekendMin(Math.min(600, store.weekendMin() + 15))),
                new Menu.Item(() -> getString(R.string.limit_today,
                        getString(store.offToday() ? R.string.state_off : R.string.state_on)),
                        () -> store.setOffToday(!store.offToday()), null, null),
                Menu.item(getString(R.string.reset_today), store::resetToday),
                Menu.item(getString(R.string.change_pin), () -> start(Stage.CREATE)),
                Menu.item(getString(R.string.exit), this::finish)));
    }

    private String status() {
        long used = store.usedMs() / 60_000;
        long left = (store.remainingMs() + 59_999) / 60_000;
        String service = getString(serviceEnabled() ? R.string.service_on : R.string.service_off);
        return getString(R.string.status, (int) used, (int) left, service);
    }

    private boolean serviceEnabled() {
        String list = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return list != null && list.contains(getPackageName() + "/");
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP) {
                if (stage == Stage.CONFIRM) start(Stage.CREATE);
                else finish();
            }
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            boolean handled = stage == Stage.SETTINGS ? menu.onKey(code) : pad.onKey(code);
            if (handled) return true;
        }
        return super.dispatchKeyEvent(e);
    }
}

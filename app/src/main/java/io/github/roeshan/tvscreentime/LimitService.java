package io.github.roeshan.tvscreentime;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.List;

/**
 * Counts screen time in the tracked apps, warns before the daily quota runs out, then covers the whole
 * screen (apps, HDMI and Live TV alike) with a PIN lock and puts the device to standby if nobody unlocks it.
 * The lock lives in an accessibility overlay and reads the remote through onKeyEvent, so Home, Back and
 * the app buttons can't get around it; on a phone or tablet it is used by touch.
 *
 * On a phone the lock stays out of the way of the system's own lock screen (so emergency calls work) and of
 * phone calls: it shows only once the device is unlocked and no call is ringing or in progress.
 */
public class LimitService extends AccessibilityService {

    private static final long TICK_MS = 5_000;
    private static final long STANDBY_AFTER_MS = 60_000;
    private static final long ADMIN_WINDOW_MS = 10 * 60_000;
    private static final long TOAST_MS = 8_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Store store;
    private WindowManager wm;
    private AudioManager audio;
    private PowerManager power;
    private KeyguardManager keyguard;

    private String foreground = "";
    private long lastTick;

    private FrameLayout lockView;
    private PinPad pad;
    private Menu menu;
    private boolean showing, inMenu, expiredMode, muted;
    private TextView toast;

    private final Runnable tick = this::tick;
    private final Runnable standby = () -> {
        if (showing && expiredMode && power.isInteractive()) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    };
    private final Runnable hideToast = this::hideToast;

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                handler.removeCallbacks(standby);
                // Shown again once the device is on and unlocked, so it never sits over the system lock screen.
                if (showing && expiredMode) hideLock();
                return;
            }
            // SCREEN_ON (a TV, or a phone without a lock screen) or USER_PRESENT (a phone just unlocked).
            lastTick = SystemClock.elapsedRealtime();
            store.rollDay();
            if (store.exhausted() && canLock()) showLock(true);
        }
    };

    @Override
    protected void onServiceConnected() {
        store = new Store(this);
        wm = getSystemService(WindowManager.class);
        audio = getSystemService(AudioManager.class);
        power = getSystemService(PowerManager.class);
        keyguard = getSystemService(KeyguardManager.class);
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screen, f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // React to a ringing phone at once instead of on the next tick.
            audio.addOnModeChangedListener(getMainExecutor(), mode -> {
                handler.removeCallbacks(tick);
                handler.post(tick);
            });
        }
        lastTick = SystemClock.elapsedRealtime();
        handler.post(tick);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        if (pkg.equals(getPackageName()) || Apps.IGNORED.contains(pkg)) return;
        foreground = pkg;
        checkGate(pkg);
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try {
            unregisterReceiver(screen);
        } catch (IllegalArgumentException ignored) {
        }
        hideLock();
        hideToast();
        super.onDestroy();
    }

    /** Status for `adb shell dumpsys activity service io.github.roeshan.tvscreentime/.LimitService`; the shell only, never the TV. */
    @Override
    protected void dump(FileDescriptor fd, PrintWriter out, String[] args) {
        store.rollDay();
        out.println("pin set:        " + store.hasPin());
        out.println("off today:      " + store.offToday());
        out.println("quota today:    " + store.baseMin() + " min + " + store.bonusMin() + " bonus");
        out.println("used today:     " + store.usedMs() / 1000 / 60 + " min " + store.usedMs() / 1000 % 60 + " s");
        out.println("remaining:      " + store.remainingMs() / 1000 / 60 + " min " + store.remainingMs() / 1000 % 60 + " s");
        out.println("weekday/weekend:" + store.weekdayMin() + " / " + store.weekendMin() + " min");
        out.println("current app:    " + currentApp() + (Apps.TRACKED.contains(currentApp()) ? " (counted)" : ""));
        out.println("lock showing:   " + showing + (showing ? (expiredMode ? " (quota)" : " (settings gate)") : ""));
    }

    /** The topmost app window; falls back to the last window-change event. */
    private String currentApp() {
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            for (AccessibilityWindowInfo w : windows) {
                if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                AccessibilityNodeInfo root = w.getRoot();
                if (root == null || root.getPackageName() == null) continue;
                String pkg = root.getPackageName().toString();
                if (!pkg.equals(getPackageName()) && !Apps.IGNORED.contains(pkg)) return pkg;
            }
        } catch (RuntimeException ignored) {
        }
        return foreground;
    }

    private void tick() {
        long now = SystemClock.elapsedRealtime();
        long dt = Math.min(now - lastTick, 2 * TICK_MS);
        lastTick = now;
        store.rollDay();

        // A new day (or more minutes set from the settings screen) lifts an expired lock by itself, and a call or
        // the system lock screen puts it aside until they are over.
        if (showing && (!canLock() || (expiredMode && !store.exhausted()))) hideLock();

        String app = currentApp();
        if (!showing && canLock() && store.active()) {
            if (Apps.TRACKED.contains(app)) {
                store.addUsed(dt);
                long left = store.remainingMs();
                if (left <= 0) {
                    showLock(true);
                } else if (left <= 60_000 && store.warned() < 2) {
                    store.setWarned(2);
                    showToast(getResources().getQuantityString(R.plurals.minutes_left, 1, 1));
                } else if (left <= 5 * 60_000 && store.warned() < 1) {
                    store.setWarned(1);
                    showToast(getResources().getQuantityString(R.plurals.minutes_left, 5, 5));
                }
            } else if (store.exhausted()) {
                showLock(true);
            }
        }
        checkGate(app);
        handler.postDelayed(tick, TICK_MS);
    }

    /** The screen is on, unlocked, and no phone call is ringing or going on. */
    private boolean canLock() {
        if (!power.isInteractive() || keyguard.isKeyguardLocked()) return false;
        int mode = audio.getMode();
        return mode != AudioManager.MODE_RINGTONE && mode != AudioManager.MODE_IN_CALL
                && mode != AudioManager.MODE_IN_COMMUNICATION && mode != AudioManager.MODE_CALL_SCREENING;
    }

    private void checkGate(String app) {
        if (showing || !store.hasPin() || !Apps.GATED.contains(app) || !canLock()) return;
        if (System.currentTimeMillis() < store.adminUntil()) return;
        showLock(false);
    }

    // ---- lock overlay ----

    private void showLock(boolean expired) {
        if (lockView == null) {
            lockView = new FrameLayout(this);
            lockView.setBackgroundColor(Ui.SCRIM);
            pad = new PinPad(this, this::onPin);
            menu = new Menu(this);
        }
        expiredMode = expired;
        inMenu = false;
        pad.reset();
        if (expired) {
            pad.setTitle(getString(R.string.lock_expired_title), getString(R.string.lock_expired_sub));
            pad.setCancel(null, null);
        } else {
            pad.setTitle(getString(R.string.lock_gate_title), getString(R.string.lock_gate_sub));
            pad.setCancel(getString(R.string.cancel), this::leaveGate);
        }
        showContent(pad.root);
        if (!showing) {
            wm.addView(lockView, fullScreen());
            showing = true;
        }
        if (expired) {
            pauseAndMute();
            armStandby();
        }
    }

    private void hideLock() {
        handler.removeCallbacks(standby);
        if (showing) {
            wm.removeView(lockView);
            showing = false;
        }
        if (muted) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
            muted = false;
        }
    }

    private void showContent(View v) {
        lockView.removeAllViews();
        lockView.addView(Ui.column(this, v));
    }

    private void armStandby() {
        handler.removeCallbacks(standby);
        handler.postDelayed(standby, STANDBY_AFTER_MS);
    }

    private void pauseAndMute() {
        long t = SystemClock.uptimeMillis();
        audio.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0));
        audio.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE, 0));
        if (!muted) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
            muted = true;
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        if (!showing) return false;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_SLEEP:
            case KeyEvent.KEYCODE_WAKEUP:
                return false;
            default:
                break;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (expiredMode) armStandby();
            handleKey(e.getKeyCode());
        }
        return true;
    }

    private void handleKey(int code) {
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (inMenu) {
                inMenu = false;
                pad.reset();
                showContent(pad.root);
            } else if (!expiredMode) {
                leaveGate();
            }
            return;
        }
        if (inMenu) menu.onKey(code);
        else pad.onKey(code);
    }

    private void onPin(String pin) {
        long wait = store.lockoutLeftMs();
        if (wait > 0) {
            pad.error(getString(R.string.pin_lockout, (int) (wait / 1000 + 1)));
            return;
        }
        if (!store.checkPin(pin)) {
            pad.error(getString(R.string.pin_wrong));
            return;
        }
        inMenu = true;
        if (expiredMode) {
            menu.set(getString(R.string.continue_title), () -> getString(R.string.quota_today, store.baseMin()), List.of(
                    Menu.item(getString(R.string.add_minutes, 15), () -> grant(15)),
                    Menu.item(getString(R.string.add_minutes, 30), () -> grant(30)),
                    Menu.item(getString(R.string.add_minutes, 60), () -> grant(60)),
                    Menu.item(getString(R.string.off_today), () -> {
                        store.setOffToday(true);
                        hideLock();
                    })));
        } else {
            menu.set(getString(R.string.pin_ok), () -> getString(R.string.settings_unlocked), List.of(
                    Menu.item(getString(R.string.open), () -> {
                        store.setAdminUntil(System.currentTimeMillis() + ADMIN_WINDOW_MS);
                        hideLock();
                    }),
                    Menu.item(getString(R.string.cancel), this::leaveGate)));
        }
        showContent(menu.root);
    }

    /** Closes the PIN lock in front of Settings and goes Home instead. */
    private void leaveGate() {
        hideLock();
        performGlobalAction(GLOBAL_ACTION_HOME);
    }

    private void grant(int minutes) {
        store.addMinutes(minutes);
        hideLock();
    }

    // ---- warning toast ----

    private void showToast(String msg) {
        if (toast == null) {
            toast = Ui.text(this, 20, Ui.TEXT);
            int p = Ui.dp(this, 16);
            toast.setPadding(p * 2, p, p * 2, p);
            toast.setBackground(Ui.round(this, 0xE6202020));
        }
        toast.setText(msg);
        if (toast.getParent() == null) {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.x = Ui.dp(this, 32);
            lp.y = Ui.dp(this, 32);
            wm.addView(toast, lp);
        }
        handler.removeCallbacks(hideToast);
        handler.postDelayed(hideToast, TOAST_MS);
    }

    private void hideToast() {
        if (toast != null && toast.getParent() != null) wm.removeView(toast);
    }

    private static WindowManager.LayoutParams fullScreen() {
        return new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
    }
}

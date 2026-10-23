package io.github.roeshan.tvscreentime;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.LocalDate;

/** Daily quota, usage and PIN, kept in one SharedPreferences file. */
final class Store {

    static final int PIN_LENGTH = 4;
    private static final int MAX_FAILS = 5;
    private static final long LOCKOUT_MS = 60_000;

    private final SharedPreferences p;

    Store(Context c) {
        p = c.getSharedPreferences("limit", Context.MODE_PRIVATE);
    }

    /** Starts a fresh day at midnight: usage, bonus and "off today" reset. */
    void rollDay() {
        String today = LocalDate.now().toString();
        if (!today.equals(p.getString("day", ""))) {
            p.edit().putString("day", today).putLong("usedMs", 0).putInt("bonusMin", 0)
                    .putBoolean("offToday", false).putInt("warned", 0).apply();
        }
    }

    static boolean weekend() {
        DayOfWeek d = LocalDate.now().getDayOfWeek();
        return d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY;
    }

    int weekdayMin() { return p.getInt("weekdayMin", 20); }
    int weekendMin() { return p.getInt("weekendMin", 120); }
    void setWeekdayMin(int m) { p.edit().putInt("weekdayMin", m).apply(); }
    void setWeekendMin(int m) { p.edit().putInt("weekendMin", m).apply(); }

    int baseMin() { return weekend() ? weekendMin() : weekdayMin(); }
    int bonusMin() { return p.getInt("bonusMin", 0); }
    long quotaMs() { return (baseMin() + bonusMin()) * 60_000L; }
    long usedMs() { return p.getLong("usedMs", 0); }
    long remainingMs() { return Math.max(0, quotaMs() - usedMs()); }

    void addUsed(long ms) { p.edit().putLong("usedMs", usedMs() + ms).apply(); }

    void resetToday() {
        p.edit().putLong("usedMs", 0).putInt("bonusMin", 0).putInt("warned", 0).apply();
    }

    /** Extra minutes from now on: any overshoot past the old quota is forgiven first. */
    void addMinutes(int m) {
        long used = Math.min(usedMs(), quotaMs());
        p.edit().putLong("usedMs", used).putInt("bonusMin", bonusMin() + m).putInt("warned", 0).apply();
    }

    boolean offToday() { return p.getBoolean("offToday", false); }
    void setOffToday(boolean off) { p.edit().putBoolean("offToday", off).apply(); }

    /** Nothing is limited until a PIN has been set on the TV. */
    boolean active() { return hasPin() && !offToday(); }
    boolean exhausted() { return active() && remainingMs() <= 0; }

    int warned() { return p.getInt("warned", 0); }
    void setWarned(int level) { p.edit().putInt("warned", level).apply(); }

    long adminUntil() { return p.getLong("adminUntil", 0); }
    void setAdminUntil(long wallMs) { p.edit().putLong("adminUntil", wallMs).apply(); }

    boolean hasPin() { return p.contains("pinHash"); }

    void setPin(String pin) {
        byte[] raw = new byte[16];
        new SecureRandom().nextBytes(raw);
        String salt = hex(raw);
        p.edit().putString("pinSalt", salt).putString("pinHash", hash(salt, pin))
                .putInt("fails", 0).apply();
    }

    /** Checks the PIN and counts failures; five in a row lock entry for a minute. */
    boolean checkPin(String pin) {
        boolean ok = hasPin() && hash(p.getString("pinSalt", ""), pin).equals(p.getString("pinHash", ""));
        if (ok) {
            p.edit().putInt("fails", 0).apply();
        } else {
            int fails = p.getInt("fails", 0) + 1;
            SharedPreferences.Editor e = p.edit().putInt("fails", fails);
            if (fails >= MAX_FAILS) {
                e.putInt("fails", 0).putLong("lockoutUntil", System.currentTimeMillis() + LOCKOUT_MS);
            }
            e.apply();
        }
        return ok;
    }

    long lockoutLeftMs() {
        return Math.max(0, p.getLong("lockoutUntil", 0) - System.currentTimeMillis());
    }

    private static String hash(String salt, String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return hex(md.digest((salt + ":" + pin).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder(b.length * 2);
        for (byte x : b) s.append(String.format("%02x", x));
        return s.toString();
    }
}

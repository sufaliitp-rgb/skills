package com.simpleapplock;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** All saved settings. Everything stays in the app's private storage on the phone. */
final class Prefs {
    static final String FILE = "applock";
    static final String KEY_LOCKED_APPS = "locked_apps";

    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_PIN_SALT = "pin_salt";
    private static final String KEY_PIN_LENGTH = "pin_length";
    private static final String KEY_BIOMETRIC = "use_biometric";
    private static final String KEY_RELOCK = "relock_mode";
    private static final String KEY_FAILED = "failed_attempts";
    private static final String KEY_LOCKOUT_UNTIL = "lockout_until";

    /** Wrong PINs allowed before a pause is forced. */
    static final int ATTEMPTS_BEFORE_LOCKOUT = 5;
    static final long LOCKOUT_MS = 30_000L;

    private Prefs() {}

    static SharedPreferences get(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ---- PIN ----

    static boolean hasPin(Context c) {
        return get(c).contains(KEY_PIN_HASH);
    }

    static void setPin(Context c, String pin) {
        String salt = PinHasher.newSalt();
        get(c).edit()
                .putString(KEY_PIN_SALT, salt)
                .putString(KEY_PIN_HASH, PinHasher.hash(pin, salt))
                .putInt(KEY_PIN_LENGTH, pin.length())
                .putInt(KEY_FAILED, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L)
                .apply();
    }

    static boolean checkPin(Context c, String pin) {
        SharedPreferences p = get(c);
        return PinHasher.matches(pin, p.getString(KEY_PIN_SALT, null), p.getString(KEY_PIN_HASH, null));
    }

    static int pinLength(Context c) {
        return get(c).getInt(KEY_PIN_LENGTH, 4);
    }

    // ---- Wrong-PIN protection ----

    /** Records a wrong PIN. Returns true if this attempt started a lockout. */
    static boolean registerFailure(Context c) {
        SharedPreferences p = get(c);
        int failed = p.getInt(KEY_FAILED, 0) + 1;
        SharedPreferences.Editor e = p.edit().putInt(KEY_FAILED, failed);
        boolean lockout = failed % ATTEMPTS_BEFORE_LOCKOUT == 0;
        if (lockout) e.putLong(KEY_LOCKOUT_UNTIL, System.currentTimeMillis() + LOCKOUT_MS);
        e.apply();
        return lockout;
    }

    static void resetFailures(Context c) {
        get(c).edit().putInt(KEY_FAILED, 0).putLong(KEY_LOCKOUT_UNTIL, 0L).apply();
    }

    static long lockoutRemainingMs(Context c) {
        long remaining = get(c).getLong(KEY_LOCKOUT_UNTIL, 0L) - System.currentTimeMillis();
        // Guard against the clock being moved backwards.
        if (remaining > LOCKOUT_MS) remaining = LOCKOUT_MS;
        return Math.max(0L, remaining);
    }

    // ---- Locked apps ----

    static Set<String> lockedApps(Context c) {
        Set<String> s = get(c).getStringSet(KEY_LOCKED_APPS, Collections.emptySet());
        return new HashSet<>(s);
    }

    static void setLocked(Context c, String pkg, boolean locked) {
        Set<String> s = lockedApps(c);
        if (locked) s.add(pkg); else s.remove(pkg);
        get(c).edit().putStringSet(KEY_LOCKED_APPS, s).apply();
    }

    // ---- Options ----

    static boolean biometricEnabled(Context c) {
        return get(c).getBoolean(KEY_BIOMETRIC, true);
    }

    static void setBiometricEnabled(Context c, boolean on) {
        get(c).edit().putBoolean(KEY_BIOMETRIC, on).apply();
    }

    static int relockMode(Context c) {
        int m = get(c).getInt(KEY_RELOCK, LockPolicy.RELOCK_IMMEDIATELY);
        return (m < 0 || m >= LockPolicy.GRACE_MS.length) ? LockPolicy.RELOCK_IMMEDIATELY : m;
    }

    static void setRelockMode(Context c, int mode) {
        get(c).edit().putInt(KEY_RELOCK, mode).apply();
    }
}

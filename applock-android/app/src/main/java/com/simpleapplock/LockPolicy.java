package com.simpleapplock;

import java.util.HashMap;
import java.util.Map;

/**
 * Decides when a locked app needs the PIN screen. Pure Java so it can be unit tested.
 *
 * An app stays unlocked while it is in the foreground. Once you switch to a different app
 * (or the home screen) a timer starts; when you come back after the chosen grace period it
 * asks again. Turning the screen off always relocks everything.
 */
final class LockPolicy {
    static final int RELOCK_IMMEDIATELY = 0;
    static final int RELOCK_1_MIN = 1;
    static final int RELOCK_5_MIN = 2;
    static final int RELOCK_SCREEN_OFF = 3;

    /** Grace period for each relock mode, indexed by the RELOCK_* constants. */
    static final long[] GRACE_MS = {0L, 60_000L, 300_000L, Long.MAX_VALUE};

    private static final long IN_FOREGROUND = -1L;

    /** Unlocked packages -> time the user left them, or IN_FOREGROUND while still in use. */
    private final Map<String, Long> unlocked = new HashMap<>();
    private String current;

    static long graceFor(int mode) {
        return (mode < 0 || mode >= GRACE_MS.length) ? 0L : GRACE_MS[mode];
    }

    /**
     * Call whenever an app comes to the foreground.
     *
     * @return true if the lock screen must be shown for {@code pkg}.
     */
    synchronized boolean onForeground(String pkg, boolean isLocked, long now, long graceMs) {
        if (!pkg.equals(current)) {
            if (current != null) {
                Long t = unlocked.get(current);
                if (t != null && t == IN_FOREGROUND) unlocked.put(current, now);
            }
            current = pkg;
        }
        if (!isLocked) return false;

        Long left = unlocked.get(pkg);
        if (left != null && (left == IN_FOREGROUND || graceMs == Long.MAX_VALUE
                || (graceMs > 0 && now - left <= graceMs))) {
            unlocked.put(pkg, IN_FOREGROUND);
            return false;
        }
        unlocked.remove(pkg);
        return true;
    }

    /** The user entered the right PIN / fingerprint for {@code pkg}. */
    synchronized void onUnlocked(String pkg, long now) {
        if (pkg == null) return;
        unlocked.put(pkg, pkg.equals(current) ? IN_FOREGROUND : now);
    }

    synchronized boolean isUnlocked(String pkg) {
        return unlocked.containsKey(pkg);
    }

    synchronized void relockAll() {
        unlocked.clear();
    }

    synchronized String current() {
        return current;
    }
}

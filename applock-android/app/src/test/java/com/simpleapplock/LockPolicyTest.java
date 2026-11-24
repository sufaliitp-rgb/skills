package com.simpleapplock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LockPolicyTest {
    private static final String WA = "com.whatsapp";
    private static final String GALLERY = "com.android.gallery3d";
    private static final String HOME = "com.android.launcher";
    private static final String CHROME = "com.android.chrome";

    private static final long NOW = 1_000_000L;
    private static final long IMMEDIATE = LockPolicy.graceFor(LockPolicy.RELOCK_IMMEDIATELY);
    private static final long ONE_MIN = LockPolicy.graceFor(LockPolicy.RELOCK_1_MIN);
    private static final long SCREEN_OFF = LockPolicy.graceFor(LockPolicy.RELOCK_SCREEN_OFF);

    @Test
    public void lockedAppAsksForPin() {
        LockPolicy p = new LockPolicy();
        assertTrue(p.onForeground(WA, true, NOW, IMMEDIATE));
    }

    @Test
    public void unlockedAppIsNotAskedAgainWhileInUse() {
        LockPolicy p = new LockPolicy();
        assertTrue(p.onForeground(WA, true, NOW, IMMEDIATE));
        p.onUnlocked(WA, NOW);
        // More window changes inside WhatsApp (chats, dialogs...)
        assertFalse(p.onForeground(WA, true, NOW + 1000, IMMEDIATE));
        assertFalse(p.onForeground(WA, true, NOW + 600_000, IMMEDIATE));
    }

    @Test
    public void unlockedAppsAreNeverLocked() {
        LockPolicy p = new LockPolicy();
        assertFalse(p.onForeground(CHROME, false, NOW, IMMEDIATE));
        assertFalse(p.onForeground(HOME, false, NOW, IMMEDIATE));
    }

    @Test
    public void leavingAndReturningRelocksImmediately() {
        LockPolicy p = new LockPolicy();
        p.onForeground(WA, true, NOW, IMMEDIATE);
        p.onUnlocked(WA, NOW);
        p.onForeground(HOME, false, NOW + 10, IMMEDIATE);
        assertTrue(p.onForeground(WA, true, NOW + 20, IMMEDIATE));
    }

    @Test
    public void graceWindowKeepsAppOpenBriefly() {
        LockPolicy p = new LockPolicy();
        p.onForeground(WA, true, NOW, ONE_MIN);
        p.onUnlocked(WA, NOW);
        p.onForeground(HOME, false, NOW + 1000, ONE_MIN);
        assertFalse(p.onForeground(WA, true, NOW + 30_000, ONE_MIN));
        // Back in WhatsApp again, leave once more; the timer restarts from this exit.
        p.onForeground(HOME, false, NOW + 40_000, ONE_MIN);
        assertFalse(p.onForeground(WA, true, NOW + 95_000, ONE_MIN));
        p.onForeground(HOME, false, NOW + 100_000, ONE_MIN);
        assertTrue(p.onForeground(WA, true, NOW + 100_000 + 61_000, ONE_MIN));
    }

    @Test
    public void screenOffOnlyModeKeepsUnlockedUntilRelockAll() {
        LockPolicy p = new LockPolicy();
        p.onForeground(GALLERY, true, NOW, SCREEN_OFF);
        p.onUnlocked(GALLERY, NOW);
        p.onForeground(HOME, false, NOW + 1, SCREEN_OFF);
        assertFalse(p.onForeground(GALLERY, true, NOW + 3_600_000, SCREEN_OFF));
        p.relockAll();
        assertTrue(p.onForeground(GALLERY, true, NOW + 3_600_001, SCREEN_OFF));
    }

    @Test
    public void eachLockedAppNeedsItsOwnUnlock() {
        LockPolicy p = new LockPolicy();
        p.onForeground(WA, true, NOW, SCREEN_OFF);
        p.onUnlocked(WA, NOW);
        assertTrue(p.onForeground(GALLERY, true, NOW + 5, SCREEN_OFF));
    }

    @Test
    public void unlockForAppNotInFrontStartsGraceTimerInsteadOfStayingOpen() {
        LockPolicy p = new LockPolicy();
        p.onForeground(WA, true, NOW, IMMEDIATE);
        p.onForeground(CHROME, false, NOW + 5, IMMEDIATE);
        p.onUnlocked(WA, NOW + 10); // late unlock while another app is in front
        assertTrue(p.onForeground(WA, true, NOW + 20, IMMEDIATE));
    }

    @Test
    public void tracksCurrentApp() {
        LockPolicy p = new LockPolicy();
        p.onForeground(CHROME, false, NOW, IMMEDIATE);
        assertEquals(CHROME, p.current());
        p.onForeground(WA, true, NOW, IMMEDIATE);
        assertEquals(WA, p.current());
    }

    @Test
    public void immediateModeRelocksEvenWithinTheSameMillisecond() {
        LockPolicy p = new LockPolicy();
        p.onForeground(WA, true, NOW, IMMEDIATE);
        p.onUnlocked(WA, NOW);
        p.onForeground(HOME, false, NOW, IMMEDIATE);
        assertTrue(p.onForeground(WA, true, NOW, IMMEDIATE));
    }

    @Test
    public void unknownModeFallsBackToImmediate() {
        assertEquals(0L, LockPolicy.graceFor(99));
        assertEquals(0L, LockPolicy.graceFor(-1));
    }
}

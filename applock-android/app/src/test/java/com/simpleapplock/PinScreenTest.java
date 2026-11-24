package com.simpleapplock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;

@RunWith(RobolectricTestRunner.class)
public class PinScreenTest {
    private Context ctx;

    @Before
    public void setUp() {
        ctx = RuntimeEnvironment.getApplication();
        Prefs.get(ctx).edit().clear().commit();
        AppLockService.POLICY.relockAll();
    }

    private static final int[] KEYS = {
            R.id.key0, R.id.key1, R.id.key2, R.id.key3, R.id.key4,
            R.id.key5, R.id.key6, R.id.key7, R.id.key8, R.id.key9,
    };

    private static void type(Activity a, String digits) {
        for (char ch : digits.toCharArray()) {
            a.findViewById(KEYS[ch - '0']).performClick();
        }
    }

    private static String text(Activity a, int id) {
        return ((TextView) a.findViewById(id)).getText().toString();
    }

    private <T extends PinActivity> T start(Class<T> cls, Intent i) {
        ActivityController<T> c = Robolectric.buildActivity(cls, i).setup();
        return c.get();
    }

    @Test
    public void createPinNeedsConfirmation() {
        PinActivity a = start(PinActivity.class, new Intent(ctx, PinActivity.class)
                .putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_SETUP));
        assertEquals("Create a PIN", text(a, R.id.pin_title));

        type(a, "1234");
        a.findViewById(R.id.key_left).performClick(); // ✓
        assertEquals("Confirm your PIN", text(a, R.id.pin_title));
        assertFalse(Prefs.hasPin(ctx));

        type(a, "1234"); // auto-submits once the length matches
        TestApps.idleFor(300);
        assertTrue(a.isFinishing());
        assertEquals(Activity.RESULT_OK, shadowOf(a).getResultCode());
        assertTrue(Prefs.checkPin(ctx, "1234"));
        assertFalse(Prefs.checkPin(ctx, "4321"));
        assertEquals(4, Prefs.pinLength(ctx));
    }

    @Test
    public void tooShortPinIsRejected() {
        PinActivity a = start(PinActivity.class, new Intent(ctx, PinActivity.class)
                .putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_SETUP));
        type(a, "12");
        a.findViewById(R.id.key_left).performClick();
        assertEquals("Use at least 4 digits", text(a, R.id.pin_message));
        assertEquals("Create a PIN", text(a, R.id.pin_title));
    }

    @Test
    public void mismatchedConfirmationStartsOver() {
        PinActivity a = start(PinActivity.class, new Intent(ctx, PinActivity.class)
                .putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_SETUP));
        type(a, "123456");
        a.findViewById(R.id.key_left).performClick();
        type(a, "123457");
        TestApps.idleFor(300);
        assertFalse(a.isFinishing());
        assertFalse(Prefs.hasPin(ctx));
        assertEquals("The PINs didn't match. Try again.", text(a, R.id.pin_message));
        assertEquals("Create a PIN", text(a, R.id.pin_title));
    }

    @Test
    public void wrongPinThenLockoutThenRightPin() {
        Prefs.setPin(ctx, "2468");
        PinActivity a = start(PinActivity.class, new Intent(ctx, PinActivity.class)
                .putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_VERIFY));

        type(a, "1111");
        TestApps.idleFor(200);
        assertFalse(a.isFinishing());
        assertEquals("Wrong PIN. Try again.", text(a, R.id.pin_message));

        for (int i = 0; i < 4; i++) {
            type(a, "1111");
            TestApps.idleFor(200);
        }
        assertTrue(text(a, R.id.pin_message).startsWith("Too many wrong tries"));
        assertFalse(a.findViewById(R.id.key5).isEnabled());
        // Even the right PIN is ignored during the pause.
        type(a, "2468");
        TestApps.idleFor(200);
        assertFalse(a.isFinishing());

        Prefs.resetFailures(ctx);
        TestApps.idleFor(1100); // ticker re-enables the keypad
        assertTrue(a.findViewById(R.id.key5).isEnabled());
        type(a, "2468");
        TestApps.idleFor(200);
        assertTrue(a.isFinishing());
        assertEquals(Activity.RESULT_OK, shadowOf(a).getResultCode());
    }

    @Test
    public void backspaceRemovesDigit() {
        Prefs.setPin(ctx, "2468");
        PinActivity a = start(PinActivity.class, new Intent(ctx, PinActivity.class)
                .putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_VERIFY));
        type(a, "249");
        a.findViewById(R.id.key_backspace).performClick();
        type(a, "68");
        TestApps.idleFor(200);
        assertTrue(a.isFinishing());
    }

    @Test
    public void lockScreenUnlocksTheApp() {
        TestApps.install(ctx, "com.whatsapp", "WhatsApp", "com.whatsapp.Main", true);
        Prefs.setPin(ctx, "2468");
        LockScreenActivity a = start(LockScreenActivity.class, new Intent(ctx, LockScreenActivity.class)
                .putExtra(PinActivity.EXTRA_PACKAGE, "com.whatsapp"));
        assertEquals("WhatsApp is locked", text(a, R.id.pin_title));
        assertTrue(LockScreenActivity.wasReceivedFor("com.whatsapp"));
        assertFalse(AppLockService.POLICY.isUnlocked("com.whatsapp"));

        type(a, "2468");
        TestApps.idleFor(200);
        assertTrue(a.isFinishing());
        assertTrue(AppLockService.POLICY.isUnlocked("com.whatsapp"));
    }

    @Test
    public void backOnLockScreenGoesHomeNotToTheApp() {
        TestApps.install(ctx, "com.whatsapp", "WhatsApp", "com.whatsapp.Main", true);
        Prefs.setPin(ctx, "2468");
        LockScreenActivity a = start(LockScreenActivity.class, new Intent(ctx, LockScreenActivity.class)
                .putExtra(PinActivity.EXTRA_PACKAGE, "com.whatsapp"));
        a.onBackPressed();
        Intent next = shadowOf(a).getNextStartedActivity();
        assertTrue(next.hasCategory(Intent.CATEGORY_HOME));
        assertTrue(a.isFinishing());
        assertFalse(AppLockService.POLICY.isUnlocked("com.whatsapp"));
    }

    @Test
    public void lockScreenSwitchesToANewlyLockedApp() {
        TestApps.install(ctx, "com.whatsapp", "WhatsApp", "com.whatsapp.Main", true);
        TestApps.install(ctx, "com.google.android.apps.photos", "Photos", "com.photos.Main", true);
        Prefs.setPin(ctx, "2468");
        ActivityController<LockScreenActivity> c = Robolectric.buildActivity(LockScreenActivity.class,
                new Intent(ctx, LockScreenActivity.class).putExtra(PinActivity.EXTRA_PACKAGE, "com.whatsapp")).setup();
        c.newIntent(new Intent(ctx, LockScreenActivity.class)
                .putExtra(PinActivity.EXTRA_PACKAGE, "com.google.android.apps.photos"));
        assertEquals("Photos is locked", text(c.get(), R.id.pin_title));
        type(c.get(), "2468");
        TestApps.idleFor(200);
        assertTrue(AppLockService.POLICY.isUnlocked("com.google.android.apps.photos"));
        assertFalse(AppLockService.POLICY.isUnlocked("com.whatsapp"));
    }
}

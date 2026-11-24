package com.simpleapplock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowAccessibilityService;

@RunWith(RobolectricTestRunner.class)
public class ServiceAndMainTest {
    private static final String WA = "com.whatsapp";
    private static final String CHROME = "com.android.chrome";
    private static final String HOME = "com.example.launcher";

    private Context ctx;

    @Before
    public void setUp() {
        ctx = RuntimeEnvironment.getApplication();
        Prefs.get(ctx).edit().clear().commit();
        AppLockService.POLICY.relockAll();
        TestApps.install(ctx, WA, "WhatsApp", "com.whatsapp.HomeActivity", true);
        TestApps.install(ctx, CHROME, "Chrome", "com.google.android.apps.chrome.Main", true);
        TestApps.install(ctx, "com.android.settings", "Settings", "com.android.settings.Settings", true);
        TestApps.installHome(ctx, HOME);
    }

    private static AccessibilityEvent windowEvent(String pkg, String cls) {
        AccessibilityEvent e = new AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        e.setPackageName(pkg);
        e.setClassName(cls);
        return e;
    }

    private AppLockService startService() {
        AppLockService s = Robolectric.setupService(AppLockService.class);
        s.onServiceConnected();
        return s;
    }

    private static Intent nextLock(AppLockService s) {
        Intent i;
        while ((i = shadowOf(s).getNextStartedActivity()) != null) {
            if (i.getComponent() != null && LockScreenActivity.class.getName().equals(i.getComponent().getClassName())) {
                return i;
            }
        }
        return null;
    }

    @Test
    public void opensLockScreenForLockedAppOnly() {
        Prefs.setLocked(ctx, WA, true);
        AppLockService s = startService();

        s.onAccessibilityEvent(windowEvent(CHROME, "com.google.android.apps.chrome.Main"));
        assertNull(nextLock(s));

        s.onAccessibilityEvent(windowEvent(WA, "com.whatsapp.HomeActivity"));
        Intent lock = nextLock(s);
        assertNotNull(lock);
        assertEquals(WA, lock.getStringExtra(PinActivity.EXTRA_PACKAGE));
        assertTrue((lock.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
    }

    @Test
    public void pickingUpNewlyLockedAppsWhileRunning() {
        AppLockService s = startService();
        s.onAccessibilityEvent(windowEvent(WA, "com.whatsapp.HomeActivity"));
        assertNull(nextLock(s));
        Prefs.setLocked(ctx, WA, true);
        TestApps.idle();
        s.onAccessibilityEvent(windowEvent(CHROME, "com.google.android.apps.chrome.Main"));
        s.onAccessibilityEvent(windowEvent(WA, "com.whatsapp.HomeActivity"));
        assertNotNull(nextLock(s));
    }

    @Test
    public void staysUnlockedInsideAppAndRelocksAfterLeaving() {
        Prefs.setLocked(ctx, WA, true);
        AppLockService s = startService();
        s.onAccessibilityEvent(windowEvent(WA, "com.whatsapp.HomeActivity"));
        assertNotNull(nextLock(s));
        AppLockService.onAppUnlocked(WA);

        // Moving around inside WhatsApp, and the keyboard popping up, don't relock.
        s.onAccessibilityEvent(windowEvent(WA, "com.whatsapp.Conversation"));
        s.onAccessibilityEvent(windowEvent("com.google.android.inputmethod.latin",
                "android.inputmethodservice.SoftInputWindow"));
        s.onAccessibilityEvent(windowEvent("com.android.systemui", "android.widget.FrameLayout"));
        s.onAccessibilityEvent(windowEvent(WA, "com.whatsapp.HomeActivity"));
        assertNull(nextLock(s));

        // Go to the home screen and come back: locked again.
        s.onAccessibilityEvent(windowEvent(HOME, "com.example.launcher.Launcher"));
        s.onAccessibilityEvent(windowEvent(WA, "com.whatsapp.HomeActivity"));
        assertNotNull(nextLock(s));
    }

    @Test
    public void screenOffRelocksAndCoversTheOpenApp() {
        Prefs.setLocked(ctx, WA, true);
        AppLockService s = startService();
        s.onAccessibilityEvent(windowEvent(WA, "com.whatsapp.HomeActivity"));
        nextLock(s);
        AppLockService.onAppUnlocked(WA);
        assertTrue(AppLockService.POLICY.isUnlocked(WA));

        ctx.sendBroadcast(new Intent(Intent.ACTION_SCREEN_OFF));
        TestApps.idle();
        assertFalse(AppLockService.POLICY.isUnlocked(WA));
        assertNotNull(nextLock(s));
    }

    @Test
    public void goesHomeIfThePhoneBlocksTheLockScreen() {
        Prefs.setLocked(ctx, WA, true);
        AppLockService s = startService();
        s.onAccessibilityEvent(windowEvent(WA, "com.whatsapp.HomeActivity"));
        assertNotNull(nextLock(s));
        // The lock screen never actually opened (as on phones that block background screens).
        TestApps.idleFor(1600);
        ShadowAccessibilityService shadow = shadowOf(s);
        assertTrue(shadow.getGlobalActionsPerformed().contains(AccessibilityService.GLOBAL_ACTION_HOME));
    }

    // ---- Main screen ----

    @Test
    public void firstLaunchAsksToCreatePin() {
        Activity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        Intent i = shadowOf(a).getNextStartedActivity();
        assertEquals(PinActivity.class.getName(), i.getComponent().getClassName());
        assertEquals(PinActivity.MODE_SETUP, i.getIntExtra(PinActivity.EXTRA_MODE, -1));
        assertEquals(View.INVISIBLE, a.findViewById(R.id.content).getVisibility());
    }

    @Test
    public void mainScreenRequiresPinAndListsApps() throws Exception {
        Prefs.setPin(ctx, "2468");
        MainActivity.onScreenOff();
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a = c.get();
        Intent verify = shadowOf(a).getNextStartedActivity();
        assertEquals(PinActivity.MODE_VERIFY, verify.getIntExtra(PinActivity.EXTRA_MODE, -1));
        assertEquals(View.INVISIBLE, a.findViewById(R.id.content).getVisibility());

        shadowOf(a).receiveResult(verify, Activity.RESULT_OK, null);
        c.pause().resume();
        assertEquals(View.VISIBLE, a.findViewById(R.id.content).getVisibility());

        LinearLayout list = a.findViewById(R.id.apps_list);
        for (int i = 0; i < 50 && list.getChildCount() == 0; i++) {
            Thread.sleep(50);
            TestApps.idle();
        }
        assertEquals(3, list.getChildCount()); // WhatsApp, Chrome, Settings (not the launcher, not us)
        // Suggested apps come first.
        TextView first = list.getChildAt(0).findViewById(R.id.app_name);
        assertEquals("Settings", first.getText().toString());

        // Lock WhatsApp from the list.
        View waRow = null;
        for (int i = 0; i < list.getChildCount(); i++) {
            TextView name = list.getChildAt(i).findViewById(R.id.app_name);
            if ("WhatsApp".contentEquals(name.getText())) waRow = list.getChildAt(i);
        }
        assertNotNull(waRow);
        waRow.performClick();
        assertTrue(((Switch) waRow.findViewById(R.id.app_switch)).isChecked());
        assertTrue(Prefs.lockedApps(ctx).contains(WA));
        assertEquals("1 locked", ((TextView) a.findViewById(R.id.apps_count)).getText().toString());

        // Search filters the list.
        ((TextView) a.findViewById(R.id.search)).setText("chr");
        int visible = 0;
        for (int i = 0; i < list.getChildCount(); i++) {
            if (list.getChildAt(i).getVisibility() == View.VISIBLE) visible++;
        }
        assertEquals(1, visible);

        // Leaving App Lock (not for its own settings) means the PIN is needed again.
        c.pause().stop();
        c.restart().start().resume();
        Intent again = shadowOf(a).getNextStartedActivity();
        assertNotNull(again);
        assertEquals(PinActivity.MODE_VERIFY, again.getIntExtra(PinActivity.EXTRA_MODE, -1));
    }

    @Test
    public void cancellingThePinClosesAppLock() {
        Prefs.setPin(ctx, "2468");
        MainActivity.onScreenOff();
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        Intent verify = shadowOf(a).getNextStartedActivity();
        shadowOf(a).receiveResult(verify, Activity.RESULT_CANCELED, null);
        assertTrue(a.isFinishing());
    }
}

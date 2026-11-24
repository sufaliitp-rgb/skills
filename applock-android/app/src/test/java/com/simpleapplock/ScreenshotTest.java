package com.simpleapplock;

import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.hardware.biometrics.BiometricManager;
import android.view.View;
import android.widget.LinearLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowBiometricManager;

import java.io.File;
import java.io.FileOutputStream;

/** Renders the main screens to PNG files (build/screenshots) for a visual check. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xhdpi")
public class ScreenshotTest {
    private Context ctx;

    @Before
    public void setUp() {
        ctx = RuntimeEnvironment.getApplication();
        Prefs.get(ctx).edit().clear().commit();
        TestApps.install(ctx, "com.whatsapp", "WhatsApp", "com.whatsapp.Main", true);
        TestApps.install(ctx, "com.google.android.apps.photos", "Photos", "com.photos.Main", true);
        TestApps.install(ctx, "com.android.settings", "Settings", "com.android.settings.Settings", true);
        TestApps.install(ctx, "com.android.chrome", "Chrome", "com.chrome.Main", true);
        TestApps.install(ctx, "com.google.android.youtube", "YouTube", "com.yt.Main", true);
        TestApps.install(ctx, "com.android.camera", "Camera", "com.camera.Main", true);
        ShadowBiometricManager bio = Shadow.extract(ctx.getSystemService(BiometricManager.class));
        bio.setCanAuthenticate(true);
    }

    private static void save(Activity a, String name) throws Exception {
        View root = a.getWindow().getDecorView();
        Bitmap b = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(b));
        File dir = new File("build/screenshots");
        dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    private static final int[] KEYS = {
            R.id.key0, R.id.key1, R.id.key2, R.id.key3, R.id.key4,
            R.id.key5, R.id.key6, R.id.key7, R.id.key8, R.id.key9,
    };

    @Test
    public void lockScreen() throws Exception {
        Prefs.setPin(ctx, "2468");
        Activity a = Robolectric.buildActivity(LockScreenActivity.class, new Intent(ctx, LockScreenActivity.class)
                .putExtra(PinActivity.EXTRA_PACKAGE, "com.whatsapp")).setup().get();
        a.findViewById(KEYS[2]).performClick();
        a.findViewById(KEYS[4]).performClick();
        TestApps.idle();
        save(a, "lock_screen");
    }

    @Test
    public void createPin() throws Exception {
        Activity a = Robolectric.buildActivity(PinActivity.class, new Intent(ctx, PinActivity.class)
                .putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_SETUP)).setup().get();
        save(a, "create_pin");
    }

    private void mainScreen(String name, boolean serviceOn) throws Exception {
        Prefs.setPin(ctx, "2468");
        Prefs.setLocked(ctx, "com.whatsapp", true);
        Prefs.setLocked(ctx, "com.google.android.apps.photos", true);
        if (serviceOn) {
            android.provider.Settings.Secure.putString(ctx.getContentResolver(),
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    ctx.getPackageName() + "/" + AppLockService.class.getName());
        }
        MainActivity.onScreenOff();
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a = c.get();
        Intent verify = shadowOf(a).getNextStartedActivity();
        shadowOf(a).receiveResult(verify, Activity.RESULT_OK, null);
        c.pause().resume();
        LinearLayout list = a.findViewById(R.id.apps_list);
        for (int i = 0; i < 50 && list.getChildCount() == 0; i++) {
            Thread.sleep(50);
            TestApps.idle();
        }
        TestApps.idle();
        save(a, name);
    }

    @Test
    public void mainOff() throws Exception {
        mainScreen("main_protection_off", false);
    }

    @Test
    public void mainOn() throws Exception {
        mainScreen("main_protection_on", true);
    }

    @Test
    @Config(qualifiers = "w393dp-h852dp-night-xhdpi")
    public void mainOnDark() throws Exception {
        mainScreen("main_protection_on_dark", true);
    }
}

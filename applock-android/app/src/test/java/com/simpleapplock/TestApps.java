package com.simpleapplock;

import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.os.Looper;

import org.robolectric.shadows.ShadowPackageManager;

import java.time.Duration;

/** Helpers that install fake apps into Robolectric's package manager. */
final class TestApps {
    private TestApps() {}

    static void install(Context c, String pkg, String label, String activity, boolean launcher) {
        ShadowPackageManager spm = shadowOf(c.getPackageManager());
        ApplicationInfo ai = new ApplicationInfo();
        ai.packageName = pkg;
        ai.nonLocalizedLabel = label;
        ai.flags = ApplicationInfo.FLAG_INSTALLED;
        PackageInfo pi = new PackageInfo();
        pi.packageName = pkg;
        pi.applicationInfo = ai;
        spm.installPackage(pi);

        ActivityInfo act = new ActivityInfo();
        act.packageName = pkg;
        act.name = activity;
        act.applicationInfo = ai;
        act.exported = true;
        spm.addOrUpdateActivity(act);

        if (launcher) {
            ResolveInfo ri = new ResolveInfo();
            ri.activityInfo = act;
            ri.nonLocalizedLabel = label;
            spm.addResolveInfoForIntent(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), ri);
        }
    }

    static void installHome(Context c, String pkg) {
        install(c, pkg, "Home", pkg + ".Launcher", false);
        ResolveInfo ri = new ResolveInfo();
        ri.activityInfo = new ActivityInfo();
        ri.activityInfo.packageName = pkg;
        ri.activityInfo.name = pkg + ".Launcher";
        shadowOf(c.getPackageManager()).addResolveInfoForIntent(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), ri);
    }

    static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    static void idleFor(long ms) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
    }
}

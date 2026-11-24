package com.simpleapplock;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.HashSet;
import java.util.Set;

/** Finds the phone's home-screen (launcher) apps, which must never be locked. */
final class HomeApps {
    private HomeApps() {}

    static Set<String> find(PackageManager pm) {
        Set<String> out = new HashSet<>();
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo ri : pm.queryIntentActivities(home, 0)) {
            // Settings registers a low-priority "fallback home" used only while booting; skip it
            // so Settings itself can still be locked.
            if (ri.activityInfo != null && ri.priority > -500) out.add(ri.activityInfo.packageName);
        }
        return out;
    }
}

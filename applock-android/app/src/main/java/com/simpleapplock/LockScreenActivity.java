package com.simpleapplock;

import android.content.Intent;
import android.os.Bundle;

/** The screen that covers a locked app until the right PIN or fingerprint is given. */
public class LockScreenActivity extends PinActivity {

    /** Package whose lock request this screen has received (proves it wasn't blocked). */
    private static volatile String sReceivedFor;
    private static volatile boolean sVisible;

    static boolean wasReceivedFor(String pkg) {
        return pkg != null && pkg.equals(sReceivedFor);
    }

    static boolean isShowingFor(String pkg) {
        return sVisible && wasReceivedFor(pkg);
    }

    @Override
    protected int modeFromIntent(Intent i) {
        return MODE_APP;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (targetPackage == null) {
            finish();
            return;
        }
        sReceivedFor = targetPackage;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String pkg = intent.getStringExtra(EXTRA_PACKAGE);
        if (pkg != null && !pkg.equals(targetPackage)) {
            cancelBiometric();
            readIntent(intent);
        } else if (!sVisible) {
            // Coming back (e.g. screen turned on again): offer the fingerprint again.
            requestAutoBiometric();
        }
        sReceivedFor = targetPackage;
    }

    @Override
    protected void onStart() {
        super.onStart();
        sVisible = true;
    }

    @Override
    protected void onStop() {
        sVisible = false;
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (targetPackage != null && targetPackage.equals(sReceivedFor)) sReceivedFor = null;
        super.onDestroy();
    }

    /** Back never reveals the locked app: it goes to the home screen instead. */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        goHome();
    }

    /** Home or Recents pressed: close the lock screen; it comes back if the app is reopened. */
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        cancelBiometric();
        finish();
    }

    private void goHome() {
        cancelBiometric();
        Intent home = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(home);
        } catch (RuntimeException ignored) {
            // No home app? Nothing else to do.
        }
        finish();
    }
}

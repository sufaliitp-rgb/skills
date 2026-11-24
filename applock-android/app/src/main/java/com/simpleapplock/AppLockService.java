package com.simpleapplock;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Watches which app is in front (only the app's package name, never screen content) and
 * shows the lock screen when a locked app opens.
 */
public class AppLockService extends AccessibilityService {

    /** Shared with the lock screen, which reports successful unlocks. */
    static final LockPolicy POLICY = new LockPolicy();

    /** If the lock screen hasn't appeared after this long, the phone blocked it. */
    private static final long SHOW_CHECK_DELAY_MS = 1500L;

    /** System screens that pop up on top of apps without really leaving them. */
    private static final String[] ALWAYS_IGNORED = {
            "android",
            "com.android.systemui",
            "com.android.intentresolver",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.google.android.gms",
    };

    private final Set<String> ignored = new HashSet<>();
    private final Set<String> homeApps = new HashSet<>();
    private final Map<String, Boolean> activityCache = new HashMap<>();
    private volatile Set<String> locked = new HashSet<>();

    private Handler handler;
    private String pendingCheckPkg;
    private boolean receiverRegistered;

    private final Runnable checkLockShown = this::onCheckLockShown;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener = (p, key) -> {
        if (Prefs.KEY_LOCKED_APPS.equals(key)) locked = Prefs.lockedApps(this);
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                onScreenOff();
            } else if (Intent.ACTION_SCREEN_ON.equals(action) || Intent.ACTION_USER_PRESENT.equals(action)) {
                lockCurrentIfNeeded();
            }
        }
    };

    static void onAppUnlocked(String pkg) {
        POLICY.onUnlocked(pkg, SystemClock.elapsedRealtime());
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        handler = new Handler(Looper.getMainLooper());
        locked = Prefs.lockedApps(this);
        Prefs.get(this).registerOnSharedPreferenceChangeListener(prefsListener);
        loadIgnoredAndHomeApps();

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, f);
        }
        receiverRegistered = true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence p = event.getPackageName();
        if (p == null) return;
        String pkg = p.toString();
        if (pkg.equals(getPackageName()) || ignored.contains(pkg)) return;

        boolean isLocked = locked.contains(pkg);
        if (!isLocked && !homeApps.contains(pkg)) {
            // Only count real app screens as "switching apps"; skip keyboards, popups, overlays.
            CharSequence cls = event.getClassName();
            if (cls == null || !isActivity(pkg, cls.toString())) return;
        }

        long grace = LockPolicy.graceFor(Prefs.relockMode(this));
        if (POLICY.onForeground(pkg, isLocked, SystemClock.elapsedRealtime(), grace)) {
            showLock(pkg);
        }
    }

    @Override
    public void onInterrupt() {
        // Nothing to interrupt: this service gives no spoken or haptic feedback.
    }

    @Override
    public void onDestroy() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (IllegalArgumentException ignoredException) {
                // Already unregistered.
            }
            receiverRegistered = false;
        }
        Prefs.get(this).unregisterOnSharedPreferenceChangeListener(prefsListener);
        if (handler != null) handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void onScreenOff() {
        POLICY.relockAll();
        MainActivity.onScreenOff();
        // Cover the app now so it is already hidden when the screen turns back on.
        lockCurrentIfNeeded();
    }

    private void lockCurrentIfNeeded() {
        String cur = POLICY.current();
        if (cur != null && locked.contains(cur) && !POLICY.isUnlocked(cur)
                && !LockScreenActivity.isShowingFor(cur)) {
            showLock(cur);
        }
    }

    private void showLock(String pkg) {
        Intent i = new Intent(this, LockScreenActivity.class)
                .putExtra(PinActivity.EXTRA_PACKAGE, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        try {
            startActivity(i);
        } catch (RuntimeException e) {
            // Fall through to the safety check below.
        }
        if (handler != null) {
            pendingCheckPkg = pkg;
            handler.removeCallbacks(checkLockShown);
            handler.postDelayed(checkLockShown, SHOW_CHECK_DELAY_MS);
        }
    }

    /**
     * Some phones (e.g. Xiaomi) block apps from opening screens in the background unless a
     * special permission is granted. If the lock screen never appeared, leave the locked app
     * by going to the home screen so it stays private, and explain what to allow.
     */
    private void onCheckLockShown() {
        String pkg = pendingCheckPkg;
        if (pkg == null) return;
        if (!locked.contains(pkg) || POLICY.isUnlocked(pkg) || !pkg.equals(POLICY.current())
                || LockScreenActivity.wasReceivedFor(pkg)) {
            return;
        }
        performGlobalAction(GLOBAL_ACTION_HOME);
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm == null || pm.isInteractive()) {
            Toast.makeText(this, R.string.toast_lock_blocked, Toast.LENGTH_LONG).show();
        }
    }

    private boolean isActivity(String pkg, String cls) {
        String key = pkg + '/' + cls;
        Boolean cached = activityCache.get(key);
        if (cached != null) return cached;
        boolean result;
        try {
            getPackageManager().getActivityInfo(new ComponentName(pkg, cls), 0);
            result = true;
        } catch (PackageManager.NameNotFoundException e) {
            result = false;
        }
        if (activityCache.size() > 500) activityCache.clear();
        activityCache.put(key, result);
        return result;
    }

    private void loadIgnoredAndHomeApps() {
        ignored.clear();
        for (String s : ALWAYS_IGNORED) ignored.add(s);
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) {
            for (InputMethodInfo info : imm.getInputMethodList()) ignored.add(info.getPackageName());
        }
        homeApps.clear();
        homeApps.addAll(HomeApps.find(getPackageManager()));
        // A keyboard or launcher must never be locked out by accident.
        ignored.removeAll(homeApps);
    }
}

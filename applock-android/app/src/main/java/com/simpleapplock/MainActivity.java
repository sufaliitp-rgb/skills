package com.simpleapplock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Home screen: protection status, options, and the list of apps to lock. */
public class MainActivity extends Activity {
    private static final int REQ_SETUP = 1;
    private static final int REQ_VERIFY = 2;
    private static final int REQ_CHANGE_PIN = 3;
    private static final String STATE_AUTH_PENDING = "auth_pending";

    /** Opening App Lock needs the PIN again after leaving it (or after the screen turns off). */
    private static volatile long sAuthValidUntil = 0L;
    /** Grace period while you're away in Settings turning things on for App Lock. */
    private static final long SETTINGS_GRACE_MS = 3 * 60_000L;

    /** Apps people most often want to lock; shown near the top of the list. */
    private static final Set<String> SUGGESTED = new HashSet<>();

    static {
        String[] s = {
                "com.whatsapp", "com.whatsapp.w4b", "com.google.android.apps.photos",
                "com.android.settings", "com.google.android.apps.messaging",
                "com.samsung.android.messaging", "org.telegram.messenger", "com.instagram.android",
                "com.facebook.orca", "com.google.android.apps.nbu.files", "com.snapchat.android",
                "com.google.android.gm",
        };
        for (String p : s) SUGGESTED.add(p);
    }

    private boolean authPending;
    private boolean leavingForSettings;
    private boolean appsLoaded;

    private View content;
    private View statusCard;
    private ImageView statusIcon;
    private TextView statusTitle;
    private TextView statusText;
    private Button turnOnButton;
    private View setupHelp;
    private TextView xiaomiHint;
    private Switch biometricSwitch;
    private TextView biometricSubtitle;
    private View biometricRow;
    private TextView relockValue;
    private TextView appsCount;
    private EditText search;
    private LinearLayout appsList;
    private TextView appsLoading;

    private final List<AppRow> rows = new ArrayList<>();

    private static final class AppRow {
        final String pkg;
        final String label;
        final View view;

        AppRow(String pkg, String label, View view) {
            this.pkg = pkg;
            this.label = label;
            this.view = view;
        }
    }

    static void onScreenOff() {
        sAuthValidUntil = 0L;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Keep the list of locked apps out of screenshots and the Recents preview.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_main);
        if (savedInstanceState != null) authPending = savedInstanceState.getBoolean(STATE_AUTH_PENDING);

        content = findViewById(R.id.content);
        statusCard = findViewById(R.id.status_card);
        statusIcon = findViewById(R.id.status_icon);
        statusTitle = findViewById(R.id.status_title);
        statusText = findViewById(R.id.status_text);
        turnOnButton = findViewById(R.id.status_button);
        setupHelp = findViewById(R.id.setup_help);
        xiaomiHint = findViewById(R.id.xiaomi_hint);
        biometricSwitch = findViewById(R.id.biometric_switch);
        biometricSubtitle = findViewById(R.id.biometric_subtitle);
        biometricRow = findViewById(R.id.row_biometric);
        relockValue = findViewById(R.id.relock_value);
        appsCount = findViewById(R.id.apps_count);
        search = findViewById(R.id.search);
        appsList = findViewById(R.id.apps_list);
        appsLoading = findViewById(R.id.apps_loading);

        turnOnButton.setOnClickListener(v -> openAccessibilitySettings());
        findViewById(R.id.app_info_button).setOnClickListener(v -> openAppInfo());
        biometricRow.setOnClickListener(v -> {
            if (biometricSwitch.isEnabled()) biometricSwitch.toggle();
        });
        biometricSwitch.setOnCheckedChangeListener((b, checked) -> Prefs.setBiometricEnabled(this, checked));
        findViewById(R.id.row_relock).setOnClickListener(v -> chooseRelock());
        findViewById(R.id.row_change_pin).setOnClickListener(v -> {
            leavingForSettings = true;
            startActivityForResult(new Intent(this, PinActivity.class)
                    .putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_SETUP), REQ_CHANGE_PIN);
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                applyFilter();
            }
        });

        String maker = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        boolean xiaomi = maker.contains("xiaomi") || maker.contains("redmi") || maker.contains("poco");
        xiaomiHint.setVisibility(xiaomi ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_AUTH_PENDING, authPending);
    }

    @Override
    protected void onResume() {
        super.onResume();
        leavingForSettings = false;
        if (SystemClock.elapsedRealtime() < sAuthValidUntil) {
            sAuthValidUntil = Long.MAX_VALUE;
            content.setVisibility(View.VISIBLE);
            refresh();
        } else {
            content.setVisibility(View.INVISIBLE);
            requestAuth();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isChangingConfigurations() || authPending) return;
        if (leavingForSettings) {
            sAuthValidUntil = Math.min(sAuthValidUntil, SystemClock.elapsedRealtime() + SETTINGS_GRACE_MS);
        } else {
            sAuthValidUntil = 0L;
        }
    }

    private void requestAuth() {
        if (authPending) return;
        authPending = true;
        Intent i = new Intent(this, PinActivity.class).putExtra(PinActivity.EXTRA_MODE,
                Prefs.hasPin(this) ? PinActivity.MODE_VERIFY : PinActivity.MODE_SETUP);
        startActivityForResult(i, Prefs.hasPin(this) ? REQ_VERIFY : REQ_SETUP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SETUP || requestCode == REQ_VERIFY) {
            authPending = false;
            if (resultCode == RESULT_OK) {
                sAuthValidUntil = Long.MAX_VALUE;
            } else {
                finish();
            }
        }
    }

    // ---- Status & options ----

    private void refresh() {
        boolean on = isServiceEnabled();
        int lockedCount = Prefs.lockedApps(this).size();
        statusCard.setBackgroundResource(on ? R.drawable.card_ok : R.drawable.card_warn);
        statusIcon.setImageResource(on ? R.drawable.ic_shield_check : R.drawable.ic_warning);
        statusTitle.setText(on ? R.string.status_on : R.string.status_off);
        statusTitle.setTextColor(getColor(on ? R.color.ok_fg : R.color.warn_fg));
        if (on) {
            statusText.setText(lockedCount == 0 ? getString(R.string.status_on_none)
                    : getResources().getQuantityString(R.plurals.status_on_count, lockedCount, lockedCount));
        } else {
            statusText.setText(R.string.status_off_text);
        }
        turnOnButton.setVisibility(on ? View.GONE : View.VISIBLE);
        setupHelp.setVisibility(on ? View.GONE : View.VISIBLE);

        boolean bioAvailable = BiometricHelper.isAvailable(this);
        biometricSwitch.setEnabled(bioAvailable);
        biometricRow.setAlpha(bioAvailable ? 1f : 0.55f);
        biometricSwitch.setChecked(bioAvailable && Prefs.biometricEnabled(this));
        biometricSubtitle.setText(bioAvailable ? R.string.opt_biometric_sub : R.string.opt_biometric_unavailable);

        relockValue.setText(getResources().getStringArray(R.array.relock_options)[Prefs.relockMode(this)]);
        updateCount();
        if (!appsLoaded) loadApps();
    }

    private void updateCount() {
        int n = Prefs.lockedApps(this).size();
        appsCount.setText(getResources().getQuantityString(R.plurals.locked_count, n, n));
    }

    private void chooseRelock() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.opt_relock)
                .setSingleChoiceItems(R.array.relock_options, Prefs.relockMode(this), (d, which) -> {
                    Prefs.setRelockMode(this, which);
                    relockValue.setText(getResources().getStringArray(R.array.relock_options)[which]);
                    d.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        ComponentName me = new ComponentName(this, AppLockService.class);
        for (String s : enabled.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(s);
            if (me.equals(cn)) return true;
        }
        return false;
    }

    private void openAccessibilitySettings() {
        Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        // Ask Settings to highlight App Lock in the list where supported.
        String key = new ComponentName(this, AppLockService.class).flattenToString();
        Bundle args = new Bundle();
        args.putString(":settings:fragment_args_key", key);
        i.putExtra(":settings:fragment_args_key", key);
        i.putExtra(":settings:show_fragment_args", args);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        leavingForSettings = true;
        try {
            startActivity(i);
        } catch (RuntimeException e) {
            leavingForSettings = false;
        }
    }

    private void openAppInfo() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        leavingForSettings = true;
        try {
            startActivity(i);
        } catch (RuntimeException e) {
            leavingForSettings = false;
        }
    }

    // ---- App list ----

    private static final class AppInfo {
        String pkg;
        String label;
        Drawable icon;
        boolean suggested;
    }

    private void loadApps() {
        appsLoaded = true;
        appsLoading.setVisibility(View.VISIBLE);
        final PackageManager pm = getPackageManager();
        final Set<String> lockedNow = Prefs.lockedApps(this);
        final String self = getPackageName();
        new Thread(() -> {
            Set<String> home = HomeApps.find(pm);
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> found = pm.queryIntentActivities(launcher, 0);
            List<AppInfo> apps = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (ResolveInfo ri : found) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(self) || home.contains(pkg) || !seen.add(pkg)) continue;
                AppInfo a = new AppInfo();
                a.pkg = pkg;
                CharSequence label = ri.loadLabel(pm);
                a.label = label == null ? pkg : label.toString();
                a.icon = ri.loadIcon(pm);
                String lower = pkg.toLowerCase(Locale.ROOT);
                a.suggested = SUGGESTED.contains(pkg) || lower.contains("gallery") || lower.contains("photos");
                apps.add(a);
            }
            Collator collator = Collator.getInstance();
            apps.sort((x, y) -> {
                int rx = lockedNow.contains(x.pkg) ? 0 : x.suggested ? 1 : 2;
                int ry = lockedNow.contains(y.pkg) ? 0 : y.suggested ? 1 : 2;
                if (rx != ry) return Integer.compare(rx, ry);
                return collator.compare(x.label, y.label);
            });
            runOnUiThread(() -> showApps(apps));
        }, "load-apps").start();
    }

    private void showApps(List<AppInfo> apps) {
        if (isDestroyed()) return;
        appsLoading.setVisibility(View.GONE);
        appsList.removeAllViews();
        rows.clear();
        Set<String> lockedNow = Prefs.lockedApps(this);
        LayoutInflater inflater = getLayoutInflater();
        for (AppInfo a : apps) {
            View row = inflater.inflate(R.layout.item_app, appsList, false);
            ((ImageView) row.findViewById(R.id.app_icon)).setImageDrawable(a.icon);
            ((TextView) row.findViewById(R.id.app_name)).setText(a.label);
            TextView hint = row.findViewById(R.id.app_hint);
            if (a.suggested) {
                hint.setVisibility(View.VISIBLE);
                hint.setText("com.android.settings".equals(a.pkg)
                        ? R.string.hint_settings : R.string.hint_suggested);
            } else {
                hint.setVisibility(View.GONE);
            }
            Switch sw = row.findViewById(R.id.app_switch);
            sw.setChecked(lockedNow.contains(a.pkg));
            sw.setContentDescription(getString(R.string.lock_app_cd, a.label));
            sw.setOnCheckedChangeListener((b, checked) -> {
                Prefs.setLocked(this, a.pkg, checked);
                updateCount();
                if (isServiceEnabled()) refreshStatusTextOnly();
            });
            row.setOnClickListener(v -> sw.toggle());
            appsList.addView(row);
            rows.add(new AppRow(a.pkg, a.label.toLowerCase(Locale.getDefault()), row));
        }
        applyFilter();
    }

    private void refreshStatusTextOnly() {
        int n = Prefs.lockedApps(this).size();
        statusText.setText(n == 0 ? getString(R.string.status_on_none)
                : getResources().getQuantityString(R.plurals.status_on_count, n, n));
    }

    private void applyFilter() {
        String q = search.getText().toString().trim().toLowerCase(Locale.getDefault());
        for (AppRow r : rows) {
            boolean show = q.isEmpty() || r.label.contains(q) || r.pkg.contains(q);
            r.view.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }
}

package com.simpleapplock;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.CycleInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Number-pad screen. Used to create a PIN, to open App Lock itself, and (through
 * {@link LockScreenActivity}) to unlock a locked app.
 */
public class PinActivity extends Activity {
    static final String EXTRA_MODE = "mode";
    static final String EXTRA_PACKAGE = "package";

    static final int MODE_VERIFY = 0;
    static final int MODE_SETUP = 1;
    static final int MODE_APP = 2;

    static final int MIN_PIN = 4;
    static final int MAX_PIN = 8;

    private static final int[] DIGIT_IDS = {
            R.id.key0, R.id.key1, R.id.key2, R.id.key3, R.id.key4,
            R.id.key5, R.id.key6, R.id.key7, R.id.key8, R.id.key9,
    };

    protected int mode;
    protected String targetPackage;

    private final StringBuilder entered = new StringBuilder();
    private String firstPin;
    private boolean busy;
    private boolean autoBiometric;
    private CancellationSignal biometricCancel;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private ImageView icon;
    private TextView title;
    private TextView subtitle;
    private TextView message;
    private LinearLayout dots;
    private ImageButton leftKey;

    private final Runnable startBiometricRunnable = this::startBiometric;
    private final Runnable lockoutTicker = this::updateLockout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin);
        icon = findViewById(R.id.pin_icon);
        title = findViewById(R.id.pin_title);
        subtitle = findViewById(R.id.pin_subtitle);
        message = findViewById(R.id.pin_message);
        dots = findViewById(R.id.pin_dots);
        leftKey = findViewById(R.id.key_left);

        for (int d = 0; d < DIGIT_IDS.length; d++) {
            final char digit = (char) ('0' + d);
            findViewById(DIGIT_IDS[d]).setOnClickListener(v -> onDigit(digit));
        }
        View back = findViewById(R.id.key_backspace);
        back.setOnClickListener(v -> onBackspace());
        back.setOnLongClickListener(v -> {
            entered.setLength(0);
            updateDots();
            return true;
        });
        leftKey.setOnClickListener(v -> {
            if (mode == MODE_SETUP) {
                submitSetup();
            } else {
                startBiometric();
            }
        });

        readIntent(getIntent());
    }

    /** Show the fingerprint prompt automatically the next time this screen resumes. */
    protected void requestAutoBiometric() {
        autoBiometric = true;
    }

    /** Which mode an incoming intent asks for. */
    protected int modeFromIntent(Intent i) {
        return i.getIntExtra(EXTRA_MODE, MODE_VERIFY);
    }

    protected void readIntent(Intent i) {
        mode = modeFromIntent(i);
        targetPackage = i.getStringExtra(EXTRA_PACKAGE);
        entered.setLength(0);
        firstPin = null;
        busy = false;
        autoBiometric = true;
        message.setText("");
        renderHeader();
        updateDots();
        updateLeftKey();
        updateLockout();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateLockout();
        if (autoBiometric) {
            autoBiometric = false;
            handler.postDelayed(startBiometricRunnable, 250);
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(startBiometricRunnable);
        handler.removeCallbacks(lockoutTicker);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        cancelBiometric();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ---- Header ----

    private void renderHeader() {
        if (mode == MODE_APP) {
            CharSequence label = targetPackage;
            Drawable appIcon = null;
            PackageManager pm = getPackageManager();
            try {
                ApplicationInfo ai = pm.getApplicationInfo(targetPackage, 0);
                label = pm.getApplicationLabel(ai);
                appIcon = pm.getApplicationIcon(ai);
            } catch (PackageManager.NameNotFoundException | NullPointerException e) {
                // Show the package name and our own icon instead.
            }
            if (appIcon != null) {
                icon.setImageDrawable(appIcon);
                icon.setBackground(null);
                icon.setPadding(0, 0, 0, 0);
            } else {
                setOwnIcon();
            }
            title.setText(getString(R.string.lock_title_app, label));
            subtitle.setText(biometricUsable() ? R.string.lock_subtitle_bio : R.string.lock_subtitle_pin);
        } else if (mode == MODE_SETUP) {
            setOwnIcon();
            if (firstPin == null) {
                title.setText(Prefs.hasPin(this) ? R.string.setup_title_new : R.string.setup_title);
                subtitle.setText(R.string.setup_subtitle);
            } else {
                title.setText(R.string.setup_confirm_title);
                subtitle.setText(R.string.setup_confirm_subtitle);
            }
        } else {
            setOwnIcon();
            title.setText(R.string.app_name);
            subtitle.setText(biometricUsable() ? R.string.lock_subtitle_bio : R.string.lock_subtitle_pin);
        }
    }

    private void setOwnIcon() {
        icon.setImageResource(R.drawable.ic_lock);
        icon.setBackgroundResource(R.drawable.pin_icon_bg);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        icon.setPadding(pad, pad, pad, pad);
    }

    private void updateLeftKey() {
        if (mode == MODE_SETUP) {
            leftKey.setImageResource(R.drawable.ic_check);
            leftKey.setContentDescription(getString(R.string.ok));
            leftKey.setVisibility(View.VISIBLE);
        } else {
            leftKey.setImageResource(R.drawable.ic_fingerprint);
            leftKey.setContentDescription(getString(R.string.use_fingerprint));
            leftKey.setVisibility(biometricUsable() ? View.VISIBLE : View.INVISIBLE);
        }
    }

    // ---- Dots ----

    private int slotCount() {
        if (mode == MODE_SETUP) {
            return firstPin != null ? firstPin.length() : Math.max(MIN_PIN, entered.length());
        }
        return Prefs.pinLength(this);
    }

    private void updateDots() {
        int slots = slotCount();
        int size = Math.round(14 * getResources().getDisplayMetrics().density);
        int margin = Math.round(9 * getResources().getDisplayMetrics().density);
        dots.removeAllViews();
        for (int i = 0; i < slots; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(i < entered.length() ? R.drawable.dot_filled : R.drawable.dot_empty);
            dots.addView(dot);
        }
    }

    // ---- Keypad ----

    private void onDigit(char d) {
        if (busy || Prefs.lockoutRemainingMs(this) > 0 || entered.length() >= MAX_PIN) return;
        entered.append(d);
        if (!isLockoutMessageShowing()) message.setText("");
        updateDots();
        if (mode == MODE_SETUP) {
            if (firstPin != null && entered.length() == firstPin.length()) {
                busy = true;
                handler.postDelayed(() -> {
                    busy = false;
                    submitSetup();
                }, 120);
            }
        } else if (entered.length() == Prefs.pinLength(this)) {
            busy = true;
            // Let the last dot draw before the (short) hash check runs.
            handler.postDelayed(this::verifyPin, 80);
        }
    }

    private void onBackspace() {
        if (busy || entered.length() == 0) return;
        entered.setLength(entered.length() - 1);
        updateDots();
    }

    private void verifyPin() {
        String pin = entered.toString();
        if (Prefs.checkPin(this, pin)) {
            Prefs.resetFailures(this);
            onAuthenticated();
            return;
        }
        busy = false;
        entered.setLength(0);
        boolean lockout = Prefs.registerFailure(this);
        wrong(lockout ? null : getString(R.string.wrong_pin));
        updateDots();
        if (lockout) updateLockout();
    }

    private void submitSetup() {
        String pin = entered.toString();
        if (firstPin == null) {
            if (pin.length() < MIN_PIN) {
                wrong(getString(R.string.setup_too_short, MIN_PIN));
                return;
            }
            firstPin = pin;
            entered.setLength(0);
            message.setText("");
            renderHeader();
            updateDots();
        } else if (pin.equals(firstPin)) {
            Prefs.setPin(this, pin);
            Toast.makeText(this, R.string.pin_saved, Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        } else {
            firstPin = null;
            entered.setLength(0);
            renderHeader();
            updateDots();
            wrong(getString(R.string.setup_mismatch));
        }
    }

    private void wrong(String text) {
        if (text != null) message.setText(text);
        TranslateAnimation shake = new TranslateAnimation(0, 24 * getResources().getDisplayMetrics().density, 0, 0);
        shake.setDuration(400);
        shake.setInterpolator(new CycleInterpolator(3));
        dots.startAnimation(shake);
        dots.performHapticFeedback(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? HapticFeedbackConstants.REJECT : HapticFeedbackConstants.LONG_PRESS);
    }

    // ---- Lockout after too many wrong PINs ----

    private boolean isLockoutMessageShowing() {
        return message.getTag() != null;
    }

    private void updateLockout() {
        handler.removeCallbacks(lockoutTicker);
        long remaining = mode == MODE_SETUP ? 0 : Prefs.lockoutRemainingMs(this);
        boolean locked = remaining > 0;
        setKeypadEnabled(!locked);
        if (locked) {
            long secs = (remaining + 999) / 1000;
            message.setText(getString(R.string.too_many_attempts, secs));
            message.setTag(Boolean.TRUE);
            handler.postDelayed(lockoutTicker, 1000);
        } else if (isLockoutMessageShowing()) {
            message.setText("");
            message.setTag(null);
        }
    }

    private void setKeypadEnabled(boolean enabled) {
        // The fingerprint key stays usable during a lockout.
        for (int id : DIGIT_IDS) setKeyEnabled(findViewById(id), enabled);
        setKeyEnabled(findViewById(R.id.key_backspace), enabled);
    }

    private static void setKeyEnabled(View key, boolean enabled) {
        key.setEnabled(enabled);
        key.setAlpha(enabled ? 1f : 0.35f);
    }

    // ---- Fingerprint ----

    private boolean biometricUsable() {
        return mode != MODE_SETUP && Prefs.biometricEnabled(this) && BiometricHelper.isAvailable(this);
    }

    private void startBiometric() {
        if (!biometricUsable() || biometricCancel != null || isFinishing()) return;
        biometricCancel = new CancellationSignal();
        BiometricHelper.authenticate(this, title.getText(), biometricCancel, new BiometricHelper.Callback() {
            @Override
            public void onSuccess() {
                biometricCancel = null;
                Prefs.resetFailures(PinActivity.this);
                onAuthenticated();
            }

            @Override
            public void onError(int code, CharSequence msg) {
                biometricCancel = null;
                if (!BiometricHelper.isQuietError(code) && msg != null && !isLockoutMessageShowing()) {
                    message.setText(msg);
                }
            }
        });
    }

    protected void cancelBiometric() {
        if (biometricCancel != null) {
            biometricCancel.cancel();
            biometricCancel = null;
        }
    }

    // ---- Result ----

    protected void onAuthenticated() {
        cancelBiometric();
        if (mode == MODE_APP) {
            AppLockService.onAppUnlocked(targetPackage);
        } else {
            setResult(RESULT_OK);
        }
        finish();
    }
}

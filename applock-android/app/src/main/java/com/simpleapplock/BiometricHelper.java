package com.simpleapplock;

import android.app.Activity;
import android.content.Context;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.CancellationSignal;

/** Thin wrapper around the built-in Android fingerprint prompt (Android 9 and newer). */
final class BiometricHelper {

    /** Error code used when the user taps "Use PIN" on the prompt. */
    static final int ERROR_USE_PIN = -1;

    interface Callback {
        void onSuccess();

        void onError(int code, CharSequence message);
    }

    private BiometricHelper() {}

    @SuppressWarnings("deprecation")
    static boolean isAvailable(Context c) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager bm = c.getSystemService(BiometricManager.class);
            return bm != null && bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    == BiometricManager.BIOMETRIC_SUCCESS;
        }
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            BiometricManager bm = c.getSystemService(BiometricManager.class);
            return bm != null && bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS;
        }
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
            FingerprintManager fm = c.getSystemService(FingerprintManager.class);
            try {
                return fm != null && fm.isHardwareDetected() && fm.hasEnrolledFingerprints();
            } catch (SecurityException e) {
                return false;
            }
        }
        return false;
    }

    /** True for errors that just mean "the prompt was closed" and need no message. */
    static boolean isQuietError(int code) {
        return code == ERROR_USE_PIN
                || code == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED
                || code == BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                || code == 13; // BIOMETRIC_ERROR_NEGATIVE_BUTTON on some versions
    }

    static void authenticate(Activity a, CharSequence title, CancellationSignal cancel, Callback cb) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;
        BiometricPrompt.Builder b = new BiometricPrompt.Builder(a)
                .setTitle(title)
                .setSubtitle(a.getString(R.string.biometric_subtitle))
                .setNegativeButton(a.getString(R.string.use_pin), a.getMainExecutor(),
                        (dialog, which) -> cb.onError(ERROR_USE_PIN, null));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            b.setConfirmationRequired(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK);
        }
        try {
            b.build().authenticate(cancel, a.getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    cb.onSuccess();
                }

                @Override
                public void onAuthenticationError(int errorCode, CharSequence errString) {
                    cb.onError(errorCode, errString);
                }
            });
        } catch (RuntimeException e) {
            cb.onError(BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE, null);
        }
    }
}

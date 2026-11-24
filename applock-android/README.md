# App Lock (Android)

A small, ad-free app that locks the apps you choose (WhatsApp, Gallery, Photos, and so on)
behind a PIN or your fingerprint. If you hand your phone to someone, they can't open those apps.

- No ads, no trackers, no internet permission. Nothing leaves your phone.
- Your PIN is stored only as a salted hash (PBKDF2), never as plain text.
- 51 KB APK, Android 8.0 or newer. Fingerprint unlock works on Android 9 and newer.

Ready-to-install APK: [`release/AppLock-v1.0.apk`](release/AppLock-v1.0.apk)

## Install and set up

1. Copy `AppLock-v1.0.apk` to your phone and open it. If asked, allow your browser or Files app
   to "Install unknown apps". If Play Protect warns about an unknown developer, choose
   **More details → Install anyway**.
2. Open **App Lock** and create a 4–8 digit PIN.
3. Tap **Turn on**. In Accessibility settings, open **App Lock** (it may be under
   "Downloaded apps" or "Installed apps") and switch it on.
   - If the switch is greyed out or says **Restricted setting** (Android 13+): go to
     Settings → Apps → App Lock, tap **⋮** in the top corner, choose
     **Allow restricted settings**, then try again.
4. Switch on the apps you want to lock, e.g. WhatsApp, Gallery, Photos.
5. Also lock **Settings**, so nobody can switch App Lock off.

Phone-specific tips:
- **Xiaomi / Redmi / POCO:** App info → Other permissions → allow
  **Display pop-up windows while running in the background**. Without it the lock screen
  can't appear, so App Lock sends you to the home screen instead.
- If locks stop working after a while, set App Lock's battery usage to **Unrestricted**.

## How it works

App Lock uses an Android accessibility service to notice *which app* comes to the front. It only
receives the app's package name. It does not read screen content (`canRetrieveWindowContent` is
off). When a locked app opens, a full-screen PIN / fingerprint screen covers it.

Options:
- **Fingerprint unlock:** uses Android's built-in biometric prompt.
- **Lock again:** as soon as you leave the app (default), 1 minute or 5 minutes after you
  leave, or only when the screen turns off. Turning the screen off always relocks everything.
- **Change PIN.**
- 5 wrong PINs in a row force a 30-second wait.

Known limits (true of every app locker that doesn't need root):
- A locked app can show for a split second before the lock screen covers it.
- Someone could still uninstall App Lock from the home screen.

## Build it yourself

Needs JDK 17+ and the Android SDK (platform 35, build-tools 35).

```sh
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew testDebugUnitTest   # unit + Robolectric UI tests (also renders screenshots to app/build/screenshots)
./gradlew assembleRelease     # signed with keystore.properties if present, else the debug key
```

To sign with your own key, create `keystore.properties` (it is git-ignored):

```
storeFile=/path/to/your.jks
storePassword=...
keyAlias=...
keyPassword=...
```

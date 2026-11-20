# Riya — Romantic AI Voice Assistant & Phone Controller

Built for **Rohan** (Class 11, Saddam sir's student, Durlovepur Eidgah More).

## Push to GitHub & Install APK on Your Phone

### Option 1: Automatic APK Build via GitHub Actions (No PC Android Studio Needed)
1. Push this entire project folder to a GitHub repository (`main` or `master` branch).
2. Open your repository on GitHub -> tap the **Actions** tab.
3. Open the latest **Build Riya APK** workflow run.
4. Scroll to **Artifacts** and download **`Riya-Debug-APK`**.
5. Unzip it on your phone and tap **`app-debug.apk`** to install!

### Option 2: Build Locally in Android Studio / CLI
1. Open the project in Android Studio (Meerkat / Narwhal or newer with JDK 17+).
2. If `debug.keystore` does not exist yet in the project root, generate it once:
   ```bash
   keytool -genkey -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
   ```
3. Run **Build -> Build Bundle(s) / APK(s) -> Build APK(s)** or run `gradle :app:assembleDebug`.
4. Install `app/build/outputs/apk/debug/app-debug.apk` on your Android phone (Android 8.0+ / minSdk 26).

## First-Time Setup on Your Phone
1. Open **Riya** -> confirm your nickname (**Rohan**) and default language (**Banglish** / **Bengali**).
2. Grant permissions step by step (Microphone, Notifications, Unrestricted Battery, etc.).
3. On Android 13/14/15 sideloaded APKs, if Accessibility or Notification Access is greyed out:
   - Tap **Open App Info** inside Riya's Permission screen -> tap the **3 dots** (top-right) -> **Allow restricted settings**.
4. Paste your **Gemini API key** from [Google AI Studio](https://aistudio.google.com) on the **Connect Riya's Brain** screen and tap **Verify & Save Key** (stored locally via `EncryptedSharedPreferences`).

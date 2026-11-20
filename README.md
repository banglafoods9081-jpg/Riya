# Riya — Romantic AI Voice Assistant & Phone Controller

Built for **Rohan** (Class 11, Saddam sir's student, Durlovepur Eidgah More).

## Push to GitHub & Install APK on Your Phone

### Option 1: Automatic APK Build via GitHub Actions (No PC Required)
1. Push this repository to GitHub (`main` or `master` branch).
2. Open your GitHub repo -> **Actions** tab -> click the latest **Build Riya APK** run.
3. Download **`Riya-Debug-APK`** under **Artifacts**.
4. Unzip it on your Android phone and tap **`app-debug.apk`** to install.

### Option 2: Build Locally in Android Studio / CLI
1. Open the project in Android Studio (Meerkat / Narwhal or newer with JDK 17+).
2. If `debug.keystore` does not exist yet in the root folder, generate it once:
   ```bash
   keytool -genkey -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
   ```
3. Run `gradle :app:assembleDebug` (or **Build -> Build APK(s)**).
4. Install `app/build/outputs/apk/debug/app-debug.apk` on your phone (Android 8.0+ / minSdk 26).

---

## First-Time Phone Setup (Important for Android 13/14/15)

1. **Onboarding**: Confirm your name (**Rohan**) and default language (**Banglish** / **Bengali**).
2. **Permissions**: Grant Microphone, Notifications, Unrestricted Battery, and optional permissions (Contacts, Phone, SMS, Usage Access, Overlay, Accessibility, Notification Access).
3. **Allow Restricted Settings (for Sideloaded APK Accessibility & Notification Access)**:
   - On Android 13/14/15, Accessibility and Notification Access are greyed out for sideloaded APKs until unlocked.
   - Inside Riya's Permission screen, tap **Open App Info** (or open Phone Settings -> Apps -> Riya) -> tap the **3-dots menu** (top-right) -> **Allow restricted settings** -> return to Accessibility and turn **Riya** ON.
4. **Gemini API Key & Optional ElevenLabs Cute Voice**:
   - Visit [Google AI Studio](https://aistudio.google.com) -> **Get API key**.
   - Paste it in Riya's **Connect Riya's Brain** screen -> tap **Verify & Save Key** (encrypted locally in `EncryptedSharedPreferences`).
   - *(Optional for ultra-sweet cute girl voice)*: Paste your **ElevenLabs API Key** on the same screen, choose a preset (`Bella`, `Rachel`, `Elli`, or `Aria`), and tap **Test Voice**. Without an ElevenLabs key, Riya uses Android's built-in female voice automatically.

---

## Battery Optimization & Background Pop-Up Steps (Prevent Service Kill / App Launch Block)
- **Xiaomi / Redmi / POCO (MIUI / HyperOS)**: App Info -> **Other permissions** -> enable **Display pop-up windows while running in the background** (needed so Riya can open YouTube/WhatsApp/apps from voice or the floating bubble!) + Battery saver -> **No restrictions** + enable **Autostart** + lock Riya in Recents.
- **Oppo / Realme (ColorOS / Realme UI)**: App Info -> Battery usage -> enable **Allow background activity** + **Allow foreground activity** + Autostart.
- **Vivo / iQOO (Funtouch OS)**: App Info -> Battery -> **Unrestricted** + Background Power Consumption -> **Allow high background power usage**.
- **Samsung (One UI)**: App Info -> Battery -> **Unrestricted**.

---

## Honest Android Limitations & Troubleshooting
- **Always-on Wake Word**: Android blocks third-party apps from exclusive hardware DSP hotword access when another app holds the microphone (e.g., phone calls or voice recorders). Riya runs a foreground `SpeechRecognizer` loop when active, plus **Tap-to-Talk Orb**, **Floating Overlay Bubble**, **Home Widget**, and **Quick Settings Tile**.
- **Protected / DRM Screens**: Some banking, UPI, password, and DRM video apps block Accessibility reading or screenshots (`FLAG_SECURE`). Riya also intentionally refuses banking/UPI/PIN/OTP automation for your safety.
- **Voice Not Responding**: Ensure Google Speech Services (`Google` app) is enabled on your phone and Microphone permission is granted. You can always type commands directly in the Home or Chat input bar.

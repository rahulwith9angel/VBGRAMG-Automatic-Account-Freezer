VB-G RAM G Auto-Freeze 2026 — Android App (.APK) Source Project
==============================================================

Because this environment does not have the Android SDK / Gradle toolchain installed, pre-compiled .apk binaries cannot be signed and packaged directly here. Instead, this archive contains a complete, zero-configuration Android Studio project that wraps the portal in a Desktop-mode WebView with built-in Start / Stop controls and automatic postback recovery.

HOW IT WORKS ON ANDROID:
------------------------
1. Opens `https://vbgramgde2.dord.gov.in/vbgramg/states/Freez_ac.aspx` in Desktop Chrome mode.
2. You log in normally on your phone and select your District and Block.
3. Tap the green "Start 2026" button in the top bar:
   - Selects the first Panchayat in the Block
   - Finds all rows where `A/c VerifiedDate` contains `2026`
   - Checks them and taps `Freeze Account`
   - Re-checks the same page until zero `2026` rows remain
   - Advances through every page and every Panchayat until finished (surviving full-page ASP.NET reloads automatically via `onPageFinished` + `localStorage`).

TWO EASY WAYS TO GENERATE THE .APK FILE:
----------------------------------------
Option A (No PC Setup Needed — Free via GitHub Actions):
1. Create a free GitHub repository and upload all files from this ZIP.
2. Go to the "Actions" tab -> select "Build Android APK" -> click "Run workflow".
3. Download `VBGRAMG_AutoFreeze_2026_APK` (`app-debug.apk`) and install it on your Android phone.

Option B (Using Android Studio on Windows/Mac):
1. Extract this ZIP and open the folder in Android Studio.
2. Wait for Gradle sync to finish, then click: Build -> Build Bundle(s) / APK(s) -> Build APK(s).
3. Find your APK at: `app/build/outputs/apk/debug/app-debug.apk`.
  

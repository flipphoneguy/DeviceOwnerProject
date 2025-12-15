# Sonim Device Owner App Installer

This application is designed specifically for **Sonim XP5800 and XP3800** devices (running Android 8.1). These devices often restrict app installation, allowing only installation via ADB. This tool acts as a workaround by being set as the **Device Owner**, enabling it to install other applications (APKs and XAPKs) directly on the device without needing a computer for every install.

## Features

- **Bypass Install Restrictions:** Installs apps silently using Device Owner privileges.
- **Support for APK and XAPK/ZIP:** Handles single APK files and XAPK (Split APKs) bundles.
- **File Picker:** Built-in option to browse and select files to install.
- **"Open With" Support:** Can be launched directly from your file manager by opening an APK or XAPK file.
- **Progress Feedback:** Shows installation progress to prevent "App Not Responding" errors on large files.
- **Error Logging:** Detailed error logs are saved, and errors are displayed in a popup dialog.

## Setup

1.  **Install the App:**
    - Download the release APK.
    - Install it on your Sonim device via ADB:
      ```bash
      adb install device-owner-app.apk
      ```

2.  **Set as Device Owner:**
    - **Crucial Step:** You must set the app as the device owner using ADB. This grants the necessary permissions to install other apps.
      ```bash
      adb shell dpm set-device-owner com.example.deviceownerapp/.DeviceAdmin
      ```
    - *Note:* If you have other accounts (Google, etc.) on the device, you might need to remove them before setting the device owner.

3.  **Usage:**
    - Open the app.
    - Use the "Install App" button to pick a file, or browse to an APK/XAPK in your file manager and open it with this app.

## Building

This project is set up to be built in a **Termux** environment on Android, but can be adapted for other environments.

### Requirements (Termux)
- `aapt2`
- `ecj` (Eclipse Compiler for Java)
- `d8`
- `zip`
- `apksigner`
- `android.jar` (Android SDK Platform jar)

### Build Script
A `build.sh` script is included. Ensure your paths (especially to `android.jar` and your keystore) are correct in the script before running.

```bash
./build.sh
```

## Releases

Check the [Releases](releases) page for pre-built APKs.

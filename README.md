# Device Owner App

<div align="center">

![Downloads](https://img.shields.io/github/downloads/flipphoneguy/DeviceOwnerProject/total)

</div>

A utility application designed for **Sonim XP5800** and **XP3800** devices (Android 8.1) to facilitate local app installation without a constant ADB connection.

These devices restrict app installation, allowing only ADB or Device Owner/Admin based installs. This app acts as a Device Owner (or as a Dhizuku client) to bypass those restrictions and install APK and XAPK files directly on-device.

## Features

*   **Install APKs**: Directly install standard `.apk` files.
*   **Install XAPKs**: Supports installing `.xapk` (and `.zip`) bundles containing Split APKs. Uses robust `ZipFile` processing to handle various compression types.
*   **Dhizuku support**: Only one app on a device can be the Device Owner. If you want more than one privileged app, install [Dhizuku](https://github.com/iamr0s/Dhizuku) as the Device Owner and grant it permission — this app will then route privileged operations through Dhizuku's binder, leaving the Device Owner slot free for Dhizuku to share with other clients.
*   **App list with search & filter**: Searchable list of installed apps (matches package name or label, case-insensitive) with User / System / All filter toggles.
*   **Progress UI**: Visual feedback during installation, preventing "App Not Responding" errors on large files.
*   **Error Logging**: Detailed error logs are saved to `Android/data/com.example.deviceownerapp/files/app_errors.log`. On install failures the error dialog points to this file.
*   **Permissions Management**: Allows granting or denying runtime permissions for installed apps.
*   **Admin Management**: Easy uninstallation and admin removal via the main interface.
*   **Self-Update**: Built-in feature to check for updates and self-install them while retaining Device Owner status.

## Usage

1.  **Installation**:
    *   [Download Latest Release](https://github.com/flipphoneguy/DeviceOwnerProject/releases/latest/download/DeviceAdminApp.apk)
    *   Install via ADB for the first time: `adb install -t -r -g DeviceAdminApp.apk`
    *   **Important**: Remove the SIM card before setting the device owner. Sonim devices treat the SIM as an account and may block the command if present.
    *   Set it as Device Owner:
        ```bash
        adb shell dpm set-device-owner com.example.deviceownerapp/.DeviceAdmin
        ```
    *   **Dhizuku route** (only if you need another privileged app to coexist): install [Dhizuku](https://github.com/iamr0s/Dhizuku), make *Dhizuku* the Device Owner with the same `dpm` command for its package, then open this app and tap **Connect Dhizuku**.

2.  **Troubleshooting**:
    *   If existing accounts (Google, etc.) prevent setting Device Owner, list them with `adb shell dumpsys account` and uninstall the apps providing them (Sonim units have no account UI in Settings).
    *   If the status banner reads **"No Privileges — click here"**, tap it for the setup instructions.

3.  **Installing apps**:
    *   **Option A**: Open the app and tap **Install APK / XAPK** to pick a file.
    *   **Option B**: Open an APK/XAPK in a file manager and choose this app as the handler.

4.  **Uninstallation**:
    *   Open the app, scroll to **Danger Zone**, and tap **Uninstall App / Remove Admin**. This clears Device Owner status and then uninstalls the app itself.

## Releases

*   [GitHub Releases](https://github.com/flipphoneguy/DeviceOwnerProject/releases/latest)

## Build

This project builds entirely on-device in Termux — no Gradle, no Android Studio. The build pipeline lives in `build.sh` and uses `aapt2`, `ecj`, `d8`, `zip`, and `apksigner`.

```bash
./build.sh
```

You need `android.jar` and `framework-res.apk` in `~/.android/`, and a `debug.keystore` (path configurable in `build.sh`).

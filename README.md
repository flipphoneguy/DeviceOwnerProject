# Device Owner App

A utility application designed for **Sonim XP5800** and **XP3800** devices (Android 8.1) to facilitate local app installation without constant ADB connection.

These devices often restrict app installation, allowing only ADB or Device Owner/Admin based installations. This app acts as a Device Owner/Admin to bypass these restrictions and allow users to install APK and XAPK files directly from the device.

## Features

*   **Install APKs**: Directly install standard `.apk` files.
*   **Install XAPKs**: Supports installing `.xapk` (and `.zip`) bundles containing Split APKs. Uses robust `ZipFile` processing to handle various compression types.
*   **Progress UI**: Visual feedback during installation preventing "App Not Responding" errors on large files.
*   **Error Logging**: Detailed error logs are saved to `Android/data/com.example.deviceownerapp/files/app_errors.log`.
*   **Permissions Management**: Allows granting or denying runtime permissions for installed apps.
*   **Admin Management**: Easy uninstallation and admin removal via the main interface.
*   **Self-Update**: Built-in feature to check for updates and self-install them while retaining Device Owner status.

## Usage

1.  **Installation**:
    *   [Download Latest Release](https://github.com/flipphoneguy/DeviceOwnerProject/releases/latest/download/DeviceAdminApp.apk)
    *   Install this app via ADB for the first time: `adb install -t -r DeviceAdminApp.apk`
    *   **Important**: Remove the SIM card from the device before setting the device owner. Sonim devices treat the SIM as an account and may block the command if present.
    *   Set it as Device Owner:
        ```bash
        adb shell dpm set-device-owner com.example.deviceownerapp/.DeviceAdmin
        ```

2.  **Troubleshooting**:
    *   If you have existing accounts (Google, etc.) that prevent setting Device Owner, use `adb shell dumpsys account` to list them and remove them from Settings > Accounts.

3.  **Installing Apps**:
    *   **Option A**: Open "Device Owner App" and click "Install File" to pick an APK/XAPK.
    *   **Option B**: Use a file manager to open an APK/XAPK and select "Device Owner App" as the handler.

4.  **Uninstallation**:
    *   Open the app and click "Uninstall App". This will remove the Device Owner status and then uninstall the app itself.

## Releases

*   [GitHub Releases](https://github.com/flipphoneguy/DeviceOwnerProject/releases/latest)

## Build

To build this project, you can use the included `build.sh` script if you are in a Termux environment with the appropriate `android.jar`.

```bash
./build.sh
```

Ensure you have `aapt2`, `ecj`, `d8`, `zip`, and `apksigner` installed.

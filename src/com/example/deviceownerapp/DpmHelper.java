package com.example.deviceownerapp;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import com.rosan.dhizuku.api.Dhizuku;
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener;
import com.rosan.dhizuku.api.DhizukuUserServiceArgs;

/**
 * Helper class that abstracts DevicePolicyManager access for both
 * native Device Owner mode and Dhizuku mode.
 */
public class DpmHelper {

    private static final String TAG = "DpmHelper";
    private static final String DHIZUKU_PACKAGE = "com.rosan.dhizuku";

    public enum Mode {
        NONE,           // No Device Owner privileges
        NATIVE_OWNER,   // App is set as Device Owner via ADB
        DHIZUKU         // Using Dhizuku for Device Owner privileges
    }

    public interface PermissionCallback {
        void onResult(boolean granted);
    }

    // Cache for Dhizuku initialization state
    private static Boolean dhizukuInitialized = null;

    /**
     * Get the current active mode for Device Owner operations.
     */
    public static Mode getActiveMode(Context context) {
        DevicePolicyManager dpm = getDpm(context);
        String packageName = context.getPackageName();

        // Check native Device Owner first
        try {
            if (dpm.isDeviceOwnerApp(packageName)) {
                return Mode.NATIVE_OWNER;
            }
        } catch (Exception e) {
            Logger.log(context, TAG, "Error checking native DO: " + e.getMessage());
        }

        // Check Dhizuku
        if (isDhizukuConnected(context)) {
            return Mode.DHIZUKU;
        }

        return Mode.NONE;
    }

    /**
     * Check if Device Owner functionality is available through any mode.
     */
    public static boolean isAvailable(Context context) {
        return getActiveMode(context) != Mode.NONE;
    }

    /**
     * Check if the Dhizuku app is installed on the device.
     */
    public static boolean isDhizukuInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(DHIZUKU_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Check if Dhizuku is active and we have permission.
     */
    public static boolean isDhizukuConnected(Context context) {
        try {
            if (!isDhizukuInstalled(context)) {
                return false;
            }
            if (dhizukuInitialized == null || !dhizukuInitialized) {
                dhizukuInitialized = Dhizuku.init(context);
            }
            if (dhizukuInitialized) {
                return Dhizuku.isPermissionGranted();
            }
        } catch (Exception e) {
            Logger.log(context, TAG, "Error checking Dhizuku: " + e.getMessage());
        }
        return false;
    }

    /**
     * Try to initialize Dhizuku connection (without requesting permission).
     * Returns true if Dhizuku is available (but permission may not be granted).
     */
    public static boolean initDhizuku(Context context) {
        try {
            if (!isDhizukuInstalled(context)) {
                return false;
            }
            dhizukuInitialized = Dhizuku.init(context);
            return dhizukuInitialized;
        } catch (Exception e) {
            Logger.log(context, TAG, "Error initializing Dhizuku: " + e.getMessage());
            return false;
        }
    }

    /**
     * Request permission from Dhizuku.
     * This will open the Dhizuku app for user authorization.
     */
    public static void requestDhizukuPermission(Activity activity, final PermissionCallback callback) {
        try {
            if (!initDhizuku(activity)) {
                callback.onResult(false);
                return;
            }

            Dhizuku.requestPermission(new DhizukuRequestPermissionListener() {
                @Override
                public void onRequestPermission(int grantResult) throws RemoteException {
                    final boolean granted = grantResult == PackageManager.PERMISSION_GRANTED;
                    // Clear cache to re-check
                    dhizukuInitialized = null;
                    // Run callback on UI thread
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(granted);
                        }
                    });
                }
            });
        } catch (Exception e) {
            Logger.log(activity, TAG, "Error requesting Dhizuku permission: " + e.getMessage());
            callback.onResult(false);
        }
    }

    // ======== DPM Operations (work with both modes) ========

    /**
     * Hide or unhide an application.
     */
    public static boolean setApplicationHidden(Context context, String packageName, boolean hidden) {
        Mode mode = getActiveMode(context);
        if (mode == Mode.NONE) {
            return false;
        }

        try {
            if (mode == Mode.NATIVE_OWNER) {
                DevicePolicyManager dpm = getDpm(context);
                ComponentName admin = getAdminComponent(context);
                return dpm.setApplicationHidden(admin, packageName, hidden);
            } else {
                // Dhizuku mode - use binder wrapper
                return setApplicationHiddenDhizuku(context, packageName, hidden);
            }
        } catch (Exception e) {
            Logger.log(context, TAG, "setApplicationHidden error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if an application is hidden.
     */
    public static boolean isApplicationHidden(Context context, String packageName) {
        Mode mode = getActiveMode(context);
        if (mode == Mode.NONE) {
            return false;
        }

        try {
            if (mode == Mode.NATIVE_OWNER) {
                DevicePolicyManager dpm = getDpm(context);
                ComponentName admin = getAdminComponent(context);
                return dpm.isApplicationHidden(admin, packageName);
            } else {
                // Dhizuku mode - use binder wrapper
                return isApplicationHiddenDhizuku(context, packageName);
            }
        } catch (Exception e) {
            Logger.log(context, TAG, "isApplicationHidden error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get the permission grant state for an app.
     */
    public static int getPermissionGrantState(Context context, String packageName, String permission) {
        Mode mode = getActiveMode(context);
        if (mode == Mode.NONE) {
            return DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT;
        }

        try {
            if (mode == Mode.NATIVE_OWNER) {
                DevicePolicyManager dpm = getDpm(context);
                ComponentName admin = getAdminComponent(context);
                return dpm.getPermissionGrantState(admin, packageName, permission);
            } else {
                // Dhizuku mode
                return getPermissionGrantStateDhizuku(context, packageName, permission);
            }
        } catch (Exception e) {
            Logger.log(context, TAG, "getPermissionGrantState error: " + e.getMessage());
            return DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT;
        }
    }

    /**
     * Set the permission grant state for an app.
     */
    public static boolean setPermissionGrantState(Context context, String packageName,
                                                   String permission, int grantState) {
        Mode mode = getActiveMode(context);
        if (mode == Mode.NONE) {
            return false;
        }

        try {
            if (mode == Mode.NATIVE_OWNER) {
                DevicePolicyManager dpm = getDpm(context);
                ComponentName admin = getAdminComponent(context);
                return dpm.setPermissionGrantState(admin, packageName, permission, grantState);
            } else {
                // Dhizuku mode
                return setPermissionGrantStateDhizuku(context, packageName, permission, grantState);
            }
        } catch (Exception e) {
            Logger.log(context, TAG, "setPermissionGrantState error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Clear Device Owner status (only works in native mode).
     */
    public static void clearDeviceOwner(Context context) {
        Mode mode = getActiveMode(context);
        if (mode == Mode.NATIVE_OWNER) {
            try {
                DevicePolicyManager dpm = getDpm(context);
                dpm.clearDeviceOwnerApp(context.getPackageName());
            } catch (Exception e) {
                Logger.log(context, TAG, "clearDeviceOwner error: " + e.getMessage());
            }
        }
    }

    /**
     * Remove active admin (only works in native mode).
     */
    public static void removeActiveAdmin(Context context) {
        try {
            DevicePolicyManager dpm = getDpm(context);
            ComponentName admin = getAdminComponent(context);
            if (dpm.isAdminActive(admin)) {
                dpm.removeActiveAdmin(admin);
            }
        } catch (Exception e) {
            Logger.log(context, TAG, "removeActiveAdmin error: " + e.getMessage());
        }
    }

    // ======== Private helpers ========

    private static DevicePolicyManager getDpm(Context context) {
        return (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    private static ComponentName getAdminComponent(Context context) {
        return new ComponentName(context, DeviceAdmin.class);
    }

    // ======== Dhizuku binder operations ========
    // These use Dhizuku.binderWrapper to execute DPM operations with elevated privileges

    private static boolean setApplicationHiddenDhizuku(Context context, String packageName, boolean hidden) {
        try {
            // Get the DevicePolicyManager service binder
            Object dpmService = getDpmService();
            if (dpmService == null) return false;

            // Get the wrapped binder through Dhizuku
            IBinder originalBinder = (IBinder) dpmService.getClass()
                    .getMethod("asBinder").invoke(dpmService);
            IBinder wrappedBinder = Dhizuku.binderWrapper(originalBinder);

            // Get Dhizuku's owner component - use Dhizuku's package as caller since we're proxying through it
            ComponentName dhizukuAdmin = Dhizuku.getOwnerComponent();
            String callerPackage = dhizukuAdmin.getPackageName(); // Use Dhizuku's package, not ours

            // Build the transaction manually
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken("android.app.admin.IDevicePolicyManager");
                // Method signature varies by Android version
                // For Android 8.1 (API 27): setApplicationHidden(ComponentName admin, String callerPackage, String packageName, boolean hidden)
                data.writeInt(1); // admin is not null
                dhizukuAdmin.writeToParcel(data, 0);
                data.writeString(callerPackage); // callerPackage - must be Dhizuku's package
                data.writeString(packageName);
                data.writeInt(hidden ? 1 : 0);

                // Transaction code for setApplicationHidden (varies by Android version)
                int transactionCode = getTransactionCode("setApplicationHidden");
                wrappedBinder.transact(transactionCode, data, reply, 0);
                reply.readException();
                return reply.readInt() != 0;
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Exception e) {
            Logger.log(context, TAG, "setApplicationHiddenDhizuku error: " + e.getMessage());
            return false;
        }
    }

    private static boolean isApplicationHiddenDhizuku(Context context, String packageName) {
        try {
            Object dpmService = getDpmService();
            if (dpmService == null) return false;

            IBinder originalBinder = (IBinder) dpmService.getClass()
                    .getMethod("asBinder").invoke(dpmService);
            IBinder wrappedBinder = Dhizuku.binderWrapper(originalBinder);

            ComponentName dhizukuAdmin = Dhizuku.getOwnerComponent();
            String callerPackage = dhizukuAdmin.getPackageName(); // Use Dhizuku's package

            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken("android.app.admin.IDevicePolicyManager");
                data.writeInt(1); // admin is not null
                dhizukuAdmin.writeToParcel(data, 0);
                data.writeString(callerPackage);
                data.writeString(packageName);

                int transactionCode = getTransactionCode("isApplicationHidden");
                wrappedBinder.transact(transactionCode, data, reply, 0);
                reply.readException();
                return reply.readInt() != 0;
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Exception e) {
            Logger.log(context, TAG, "isApplicationHiddenDhizuku error: " + e.getMessage());
            return false;
        }
    }

    private static int getPermissionGrantStateDhizuku(Context context, String packageName, String permission) {
        try {
            Object dpmService = getDpmService();
            if (dpmService == null) return DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT;

            IBinder originalBinder = (IBinder) dpmService.getClass()
                    .getMethod("asBinder").invoke(dpmService);
            IBinder wrappedBinder = Dhizuku.binderWrapper(originalBinder);

            ComponentName dhizukuAdmin = Dhizuku.getOwnerComponent();
            String callerPackage = dhizukuAdmin.getPackageName(); // Use Dhizuku's package

            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken("android.app.admin.IDevicePolicyManager");
                data.writeInt(1); // admin is not null
                dhizukuAdmin.writeToParcel(data, 0);
                data.writeString(callerPackage);
                data.writeString(packageName);
                data.writeString(permission);

                int transactionCode = getTransactionCode("getPermissionGrantState");
                wrappedBinder.transact(transactionCode, data, reply, 0);
                reply.readException();
                return reply.readInt();
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Exception e) {
            Logger.log(context, TAG, "getPermissionGrantStateDhizuku error: " + e.getMessage());
            return DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT;
        }
    }

    private static boolean setPermissionGrantStateDhizuku(Context context, String packageName,
                                                          String permission, int grantState) {
        try {
            Object dpmService = getDpmService();
            if (dpmService == null) return false;

            IBinder originalBinder = (IBinder) dpmService.getClass()
                    .getMethod("asBinder").invoke(dpmService);
            IBinder wrappedBinder = Dhizuku.binderWrapper(originalBinder);

            ComponentName dhizukuAdmin = Dhizuku.getOwnerComponent();
            String callerPackage = dhizukuAdmin.getPackageName(); // Use Dhizuku's package

            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken("android.app.admin.IDevicePolicyManager");
                data.writeInt(1); // admin is not null
                dhizukuAdmin.writeToParcel(data, 0);
                data.writeString(callerPackage);
                data.writeString(packageName);
                data.writeString(permission);
                data.writeInt(grantState);

                int transactionCode = getTransactionCode("setPermissionGrantState");
                wrappedBinder.transact(transactionCode, data, reply, 0);
                reply.readException();
                return reply.readInt() != 0;
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Exception e) {
            Logger.log(context, TAG, "setPermissionGrantStateDhizuku error: " + e.getMessage());
            return false;
        }
    }

    private static Object getDpmService() {
        try {
            Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
            java.lang.reflect.Method getServiceMethod = serviceManagerClass
                    .getMethod("getService", String.class);
            IBinder binder = (IBinder) getServiceMethod.invoke(null, "device_policy");
            if (binder == null) return null;

            Class<?> stubClass = Class.forName("android.app.admin.IDevicePolicyManager$Stub");
            java.lang.reflect.Method asInterfaceMethod = stubClass
                    .getMethod("asInterface", IBinder.class);
            return asInterfaceMethod.invoke(null, binder);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get the transaction code for a DevicePolicyManager method.
     * Uses reflection to find the TRANSACTION_* field in IDevicePolicyManager.Stub
     */
    private static int getTransactionCode(String methodName) {
        try {
            Class<?> stubClass = Class.forName("android.app.admin.IDevicePolicyManager$Stub");
            String fieldName = "TRANSACTION_" + methodName;
            java.lang.reflect.Field field = stubClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getInt(null);
        } catch (Exception e) {
            // Fallback to hardcoded values for Android 8.1 (API 27)
            // These may need adjustment for different Android versions
            switch (methodName) {
                case "setApplicationHidden":
                    return 132;
                case "isApplicationHidden":
                    return 133;
                case "getPermissionGrantState":
                    return 213;
                case "setPermissionGrantState":
                    return 212;
                default:
                    return 0;
            }
        }
    }

    // ======== Package Installation Support ========

    /**
     * Create PackageInstaller.SessionParams for installation.
     */
    public static PackageInstaller.SessionParams createSessionParams(Context context) {
        return new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
    }

    /**
     * Result holder for Dhizuku installation.
     */
    public static class DhizukuInstallResult {
        public boolean success;
        public String error;
        public DhizukuInstallResult(boolean success, String error) {
            this.success = success;
            this.error = error;
        }
    }

    /**
     * Install an APK through Dhizuku's wrapped PackageInstaller binder.
     * This creates the session with Dhizuku's UID for silent installation.
     *
     * Based on AuroraStore's DhizukuInstaller approach:
     * 1. Get IPackageInstaller through Dhizuku-wrapped binder
     * 2. Create session through wrapped installer
     * 3. Open session - this returns a session that's owned by Dhizuku
     * 4. Write APK data directly to session
     * 5. Commit session
     */
    public static DhizukuInstallResult installApkThroughDhizuku(Context context, java.io.InputStream apkStream, String apkName) {
        if (getActiveMode(context) != Mode.DHIZUKU) {
            return new DhizukuInstallResult(false, "Not in Dhizuku mode");
        }

        PackageInstaller.Session session = null;
        int sessionId = -1;

        try {
            // Get the PackageInstaller service binder and wrap it
            IBinder pmBinder = getPackageManagerBinder();
            if (pmBinder == null) {
                return new DhizukuInstallResult(false, "Could not get PackageManager binder");
            }

            IBinder wrappedPmBinder = Dhizuku.binderWrapper(pmBinder);

            // Get IPackageManager from wrapped binder
            Class<?> pmStubClass = Class.forName("android.content.pm.IPackageManager$Stub");
            java.lang.reflect.Method asInterfaceMethod = pmStubClass.getMethod("asInterface", IBinder.class);
            Object iPackageManager = asInterfaceMethod.invoke(null, wrappedPmBinder);

            // Get IPackageInstaller from IPackageManager
            java.lang.reflect.Method getInstallerMethod = iPackageManager.getClass().getMethod("getPackageInstaller");
            Object iPackageInstaller = getInstallerMethod.invoke(iPackageManager);

            // Wrap the installer binder too
            java.lang.reflect.Method asBinderMethod = iPackageInstaller.getClass().getMethod("asBinder");
            IBinder installerBinder = (IBinder) asBinderMethod.invoke(iPackageInstaller);
            IBinder wrappedInstallerBinder = Dhizuku.binderWrapper(installerBinder);

            // Get IPackageInstaller interface from wrapped binder
            Class<?> installerStubClass = Class.forName("android.content.pm.IPackageInstaller$Stub");
            java.lang.reflect.Method installerAsInterface = installerStubClass.getMethod("asInterface", IBinder.class);
            Object wrappedIPackageInstaller = installerAsInterface.invoke(null, wrappedInstallerBinder);

            // Create session params
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);

            // Create session through wrapped installer
            java.lang.reflect.Method createSessionMethod = wrappedIPackageInstaller.getClass()
                    .getMethod("createSession", PackageInstaller.SessionParams.class, String.class, String.class, int.class);

            String dhizukuPackage = Dhizuku.getOwnerComponent().getPackageName();
            sessionId = (int) createSessionMethod.invoke(wrappedIPackageInstaller, params, dhizukuPackage, null, 0);

            Logger.log(context, TAG, "Created Dhizuku session: " + sessionId);

            // Open session through wrapped installer
            java.lang.reflect.Method openSessionMethod = wrappedIPackageInstaller.getClass()
                    .getMethod("openSession", int.class);
            Object iSession = openSessionMethod.invoke(wrappedIPackageInstaller, sessionId);

            // Wrap the session binder
            java.lang.reflect.Method sessionAsBinderMethod = iSession.getClass().getMethod("asBinder");
            IBinder sessionBinder = (IBinder) sessionAsBinderMethod.invoke(iSession);
            IBinder wrappedSessionBinder = Dhizuku.binderWrapper(sessionBinder);

            // Get IPackageInstallerSession from wrapped binder
            Class<?> sessionStubClass = Class.forName("android.content.pm.IPackageInstallerSession$Stub");
            java.lang.reflect.Method sessionAsInterface = sessionStubClass.getMethod("asInterface", IBinder.class);
            Object wrappedISession = sessionAsInterface.invoke(null, wrappedSessionBinder);

            // Create a PackageInstaller.Session wrapper using reflection
            // The session has a private constructor that takes IPackageInstallerSession
            java.lang.reflect.Constructor<?> sessionConstructor = PackageInstaller.Session.class
                    .getDeclaredConstructor(Class.forName("android.content.pm.IPackageInstallerSession"));
            sessionConstructor.setAccessible(true);
            session = (PackageInstaller.Session) sessionConstructor.newInstance(wrappedISession);

            // Write APK data to session
            try (java.io.OutputStream out = session.openWrite(apkName, 0, -1)) {
                byte[] buffer = new byte[65536];
                int len;
                while ((len = apkStream.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                session.fsync(out);
            }

            Logger.log(context, TAG, "Written APK to Dhizuku session");

            // Commit the session
            Intent intent = new Intent(context, InstallResultReceiver.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                flags |= 33554432; // FLAG_MUTABLE
            }
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags);
            session.commit(pendingIntent.getIntentSender());
            session.close();

            Logger.log(context, TAG, "Committed Dhizuku session");

            return new DhizukuInstallResult(true, null);

        } catch (Exception e) {
            Logger.log(context, TAG, "installApkThroughDhizuku error: " + e.getMessage());
            e.printStackTrace();
            if (session != null) {
                try { session.abandon(); } catch (Exception ignored) {}
            }
            return new DhizukuInstallResult(false, e.getMessage());
        }
    }

    private static IBinder getPackageManagerBinder() {
        try {
            Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
            java.lang.reflect.Method getServiceMethod = serviceManagerClass.getMethod("getService", String.class);
            return (IBinder) getServiceMethod.invoke(null, "package");
        } catch (Exception e) {
            return null;
        }
    }

    private static IBinder getPackageInstallerBinder() {
        try {
            Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
            java.lang.reflect.Method getServiceMethod = serviceManagerClass.getMethod("getService", String.class);
            IBinder pmBinder = (IBinder) getServiceMethod.invoke(null, "package");
            if (pmBinder == null) return null;

            // Get PackageManager stub
            Class<?> pmStubClass = Class.forName("android.content.pm.IPackageManager$Stub");
            java.lang.reflect.Method asInterfaceMethod = pmStubClass.getMethod("asInterface", IBinder.class);
            Object pm = asInterfaceMethod.invoke(null, pmBinder);

            // Get PackageInstaller from PackageManager
            java.lang.reflect.Method getInstallerMethod = pm.getClass().getMethod("getPackageInstaller");
            Object installer = getInstallerMethod.invoke(pm);

            // Get the binder from the installer proxy
            java.lang.reflect.Method asBinderMethod = installer.getClass().getMethod("asBinder");
            return (IBinder) asBinderMethod.invoke(installer);
        } catch (Exception e) {
            return null;
        }
    }

    private static int createSessionThroughBinder(IBinder installer, String callerPackage) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken("android.content.pm.IPackageInstaller");
            // SessionParams
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            data.writeInt(1); // params not null
            params.writeToParcel(data, 0);
            data.writeString(callerPackage); // installerPackageName

            // Transaction code for createSession
            int transactionCode = getPackageInstallerTransactionCode("createSession");
            installer.transact(transactionCode, data, reply, 0);
            reply.readException();
            return reply.readInt();
        } catch (Exception e) {
            return -1;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private static IBinder openSessionThroughBinder(IBinder installer, int sessionId, String callerPackage) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken("android.content.pm.IPackageInstaller");
            data.writeInt(sessionId);

            int transactionCode = getPackageInstallerTransactionCode("openSession");
            installer.transact(transactionCode, data, reply, 0);
            reply.readException();

            // Read the session binder
            IBinder sessionBinder = reply.readStrongBinder();
            return sessionBinder;
        } catch (Exception e) {
            return null;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private static void abandonSessionThroughBinder(IBinder installer, int sessionId, String callerPackage) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken("android.content.pm.IPackageInstaller");
            data.writeInt(sessionId);

            int transactionCode = getPackageInstallerTransactionCode("abandonSession");
            installer.transact(transactionCode, data, reply, 0);
            reply.readException();
        } catch (Exception ignored) {
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private static boolean writeToSessionThroughBinder(IBinder session, String name, java.io.InputStream apkStream) {
        ParcelFileDescriptor[] pipe = null;
        try {
            // Create a pipe to transfer data
            pipe = ParcelFileDescriptor.createPipe();
            final ParcelFileDescriptor readEnd = pipe[0];
            final ParcelFileDescriptor writeEnd = pipe[1];

            // Start a thread to write data to the pipe
            final java.io.InputStream finalStream = apkStream;
            Thread writerThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try (java.io.OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(writeEnd)) {
                        byte[] buffer = new byte[65536];
                        int len;
                        while ((len = finalStream.read(buffer)) != -1) {
                            out.write(buffer, 0, len);
                        }
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            });
            writerThread.start();

            // Call openWrite on the session
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken("android.content.pm.IPackageInstallerSession");
                data.writeString(name);
                data.writeLong(0); // offsetBytes
                data.writeLong(-1); // lengthBytes (-1 = unknown)

                int transactionCode = getSessionTransactionCode("openWrite");
                session.transact(transactionCode, data, reply, 0);
                reply.readException();

                // Get the ParcelFileDescriptor for writing
                ParcelFileDescriptor pfd = null;
                if (reply.readInt() != 0) {
                    pfd = ParcelFileDescriptor.CREATOR.createFromParcel(reply);
                }

                if (pfd != null) {
                    // Write the APK data through the returned fd
                    try (java.io.OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pfd);
                         java.io.InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(readEnd)) {
                        byte[] buffer = new byte[65536];
                        int len;
                        while ((len = in.read(buffer)) != -1) {
                            out.write(buffer, 0, len);
                        }
                    }
                    writerThread.join(30000);

                    // Call fsync
                    fsyncSessionThroughBinder(session, name);
                    return true;
                }
            } finally {
                data.recycle();
                reply.recycle();
            }

            writerThread.join(30000);
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static void fsyncSessionThroughBinder(IBinder session, String name) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken("android.content.pm.IPackageInstallerSession");
            data.writeString(name);

            int transactionCode = getSessionTransactionCode("fsync");
            session.transact(transactionCode, data, reply, 0);
            reply.readException();
        } catch (Exception ignored) {
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private static void commitSessionThroughBinder(Context context, IBinder session, int sessionId) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken("android.content.pm.IPackageInstallerSession");

            // Create an IntentSender for the result
            Intent intent = new Intent(Intent.ACTION_PACKAGE_ADDED);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                flags |= 33554432; // FLAG_MUTABLE
            }
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags);
            IntentSender intentSender = pendingIntent.getIntentSender();

            data.writeInt(1); // intentSender not null
            intentSender.writeToParcel(data, 0);
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                data.writeInt(0); // forTransferred = false
            }

            int transactionCode = getSessionTransactionCode("commit");
            session.transact(transactionCode, data, reply, 0);
            reply.readException();
        } catch (Exception e) {
            Logger.log(context, TAG, "commitSessionThroughBinder error: " + e.getMessage());
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private static int getPackageInstallerTransactionCode(String methodName) {
        try {
            Class<?> stubClass = Class.forName("android.content.pm.IPackageInstaller$Stub");
            String fieldName = "TRANSACTION_" + methodName;
            java.lang.reflect.Field field = stubClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getInt(null);
        } catch (Exception e) {
            // Fallback - these are approximate for Android 8.1+
            switch (methodName) {
                case "createSession": return 1;
                case "openSession": return 3;
                case "abandonSession": return 5;
                default: return 0;
            }
        }
    }

    /**
     * Commit a PackageInstaller session with Device Owner privileges.
     * In native mode, uses standard commit.
     * In Dhizuku mode, wraps the session binder for elevated privileges.
     */
    public static void commitSession(Context context, PackageInstaller.Session session,
                                     int sessionId, Intent resultIntent) {
        Mode mode = getActiveMode(context);

        try {
            if (mode == Mode.DHIZUKU) {
                commitSessionDhizuku(context, session, sessionId, resultIntent);
            } else {
                // Native mode or no privileges - use standard commit
                commitSessionStandard(context, session, sessionId, resultIntent);
            }
        } catch (Exception e) {
            Logger.log(context, TAG, "commitSession error: " + e.getMessage());
            // Fall back to standard commit
            commitSessionStandard(context, session, sessionId, resultIntent);
        }
    }

    private static void commitSessionStandard(Context context, PackageInstaller.Session session,
                                              int sessionId, Intent resultIntent) {
        try {
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            // Add FLAG_MUTABLE for Android 12+
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                flags |= 33554432; // FLAG_MUTABLE
            }
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context, sessionId, resultIntent, flags);
            session.commit(pendingIntent.getIntentSender());
        } catch (Exception e) {
            Logger.log(context, TAG, "commitSessionStandard error: " + e.getMessage());
        }
    }

    private static void commitSessionDhizuku(Context context, PackageInstaller.Session session,
                                             int sessionId, Intent resultIntent) {
        try {
            // Get the session's underlying binder
            IBinder sessionBinder = getSessionBinder(session);
            if (sessionBinder == null) {
                Logger.log(context, TAG, "Could not get session binder, falling back to standard");
                commitSessionStandard(context, session, sessionId, resultIntent);
                return;
            }

            // Wrap the binder through Dhizuku
            IBinder wrappedBinder = Dhizuku.binderWrapper(sessionBinder);

            // Create the IntentSender for the result
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                flags |= 33554432; // FLAG_MUTABLE
            }
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context, sessionId, resultIntent, flags);
            IntentSender intentSender = pendingIntent.getIntentSender();

            // Call commit through the wrapped binder
            // IPackageInstallerSession.commit(IntentSender statusReceiver, boolean forTransferred)
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken("android.content.pm.IPackageInstallerSession");
                // Write the IntentSender
                data.writeInt(1); // intentSender is not null
                intentSender.writeToParcel(data, 0);
                // forTransferred = false (for API 28+, may not exist on 27)
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    data.writeInt(0); // false
                }

                // Transaction code for commit
                int transactionCode = getSessionTransactionCode("commit");
                wrappedBinder.transact(transactionCode, data, reply, 0);
                reply.readException();
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Exception e) {
            Logger.log(context, TAG, "commitSessionDhizuku error: " + e.getMessage());
            // Fall back to standard commit
            commitSessionStandard(context, session, sessionId, resultIntent);
        }
    }

    private static IBinder getSessionBinder(PackageInstaller.Session session) {
        try {
            // PackageInstaller.Session has a mSession field of type IPackageInstallerSession
            java.lang.reflect.Field sessionField = PackageInstaller.Session.class.getDeclaredField("mSession");
            sessionField.setAccessible(true);
            Object iSession = sessionField.get(session);
            if (iSession == null) return null;

            // Get the binder from the IPackageInstallerSession
            java.lang.reflect.Method asBinderMethod = iSession.getClass().getMethod("asBinder");
            return (IBinder) asBinderMethod.invoke(iSession);
        } catch (Exception e) {
            return null;
        }
    }

    private static int getSessionTransactionCode(String methodName) {
        try {
            Class<?> stubClass = Class.forName("android.content.pm.IPackageInstallerSession$Stub");
            String fieldName = "TRANSACTION_" + methodName;
            java.lang.reflect.Field field = stubClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getInt(null);
        } catch (Exception e) {
            // Fallback for Android 8.1 (API 27)
            // commit is typically the first method
            if ("commit".equals(methodName)) {
                return 1; // FIRST_CALL_TRANSACTION + 0
            }
            return 0;
        }
    }

    // ======== Dhizuku UserService for Installation ========

    private static IDhizukuInstallService installService = null;
    private static final Object serviceLock = new Object();

    public interface InstallServiceCallback {
        void onServiceConnected(IDhizukuInstallService service);
        void onServiceDisconnected();
        void onBindingFailed(String error);
    }

    /**
     * Bind to the Dhizuku install service.
     * The service runs in Dhizuku's process with Device Owner privileges.
     */
    public static void bindInstallService(Context context, final InstallServiceCallback callback) {
        if (getActiveMode(context) != Mode.DHIZUKU) {
            callback.onBindingFailed("Not in Dhizuku mode");
            return;
        }

        synchronized (serviceLock) {
            if (installService != null) {
                callback.onServiceConnected(installService);
                return;
            }
        }

        try {
            DhizukuUserServiceArgs args = new DhizukuUserServiceArgs(
                    new ComponentName(context, DhizukuInstallService.class));

            boolean bound = Dhizuku.bindUserService(args, new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    synchronized (serviceLock) {
                        installService = IDhizukuInstallService.Stub.asInterface(service);
                    }
                    callback.onServiceConnected(installService);
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    synchronized (serviceLock) {
                        installService = null;
                    }
                    callback.onServiceDisconnected();
                }
            });

            if (!bound) {
                callback.onBindingFailed("Failed to bind to Dhizuku service");
            }
        } catch (Exception e) {
            Logger.log(context, TAG, "bindInstallService error: " + e.getMessage());
            callback.onBindingFailed(e.getMessage());
        }
    }

    /**
     * Get the cached install service, or null if not connected.
     */
    public static IDhizukuInstallService getInstallService() {
        synchronized (serviceLock) {
            return installService;
        }
    }

    /**
     * Check if we should use Dhizuku UserService for installation.
     */
    public static boolean shouldUseDhizukuInstaller(Context context) {
        return getActiveMode(context) == Mode.DHIZUKU;
    }
}

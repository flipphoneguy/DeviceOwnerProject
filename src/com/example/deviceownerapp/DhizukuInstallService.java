package com.example.deviceownerapp;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * AIDL interface for the Dhizuku UserService.
 * This service runs in Dhizuku's process with Device Owner privileges.
 */
public class DhizukuInstallService extends IDhizukuInstallService.Stub {

    /**
     * Default constructor required by Dhizuku.
     * Dhizuku instantiates this service using reflection.
     */
    public DhizukuInstallService() {
    }

    /**
     * Constructor that accepts a Context (for potential future use).
     */
    public DhizukuInstallService(Context context) {
    }

    /**
     * Get the context from the current process using reflection.
     * This works because the UserService runs in Dhizuku's process which has an Application context.
     */
    private Context getContext() {
        try {
            // Get context via ActivityThread.currentApplication()
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            java.lang.reflect.Method currentApplicationMethod = activityThreadClass.getMethod("currentApplication");
            return (Context) currentApplicationMethod.invoke(null);
        } catch (Exception e) {
            try {
                // Fallback: try AppGlobals.getInitialApplication()
                Class<?> appGlobalsClass = Class.forName("android.app.AppGlobals");
                java.lang.reflect.Method getInitialApplicationMethod = appGlobalsClass.getMethod("getInitialApplication");
                return (Context) getInitialApplicationMethod.invoke(null);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    @Override
    public int createInstallSession() throws RemoteException {
        try {
            Context context = getContext();
            if (context == null) {
                throw new RemoteException("Dhizuku context not available");
            }
            PackageInstaller installer = context.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            return installer.createSession(params);
        } catch (Exception e) {
            throw new RemoteException("Failed to create session: " + e.getMessage());
        }
    }

    @Override
    public void writeToSession(int sessionId, String name, ParcelFileDescriptor pfd) throws RemoteException {
        PackageInstaller.Session session = null;
        try {
            Context context = getContext();
            if (context == null) {
                throw new RemoteException("Dhizuku context not available");
            }
            PackageInstaller installer = context.getPackageManager().getPackageInstaller();
            session = installer.openSession(sessionId);

            try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
                 OutputStream out = session.openWrite(name, 0, -1)) {
                byte[] buffer = new byte[65536];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                session.fsync(out);
            }
        } catch (Exception e) {
            if (session != null) {
                try { session.abandon(); } catch (Exception ignored) {}
            }
            throw new RemoteException("Failed to write to session: " + e.getMessage());
        }
    }

    @Override
    public void commitSession(int sessionId) throws RemoteException {
        PackageInstaller.Session session = null;
        try {
            Context context = getContext();
            if (context == null) {
                throw new RemoteException("Dhizuku context not available");
            }
            PackageInstaller installer = context.getPackageManager().getPackageInstaller();
            session = installer.openSession(sessionId);

            Intent intent = new Intent(Intent.ACTION_PACKAGE_ADDED);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                flags |= 33554432; // FLAG_MUTABLE
            }
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context, sessionId, intent, flags);
            session.commit(pendingIntent.getIntentSender());
        } catch (Exception e) {
            if (session != null) {
                try { session.abandon(); } catch (Exception ignored) {}
            }
            throw new RemoteException("Failed to commit session: " + e.getMessage());
        }
    }

    @Override
    public void abandonSession(int sessionId) throws RemoteException {
        try {
            Context context = getContext();
            if (context == null) return;
            PackageInstaller installer = context.getPackageManager().getPackageInstaller();
            installer.abandonSession(sessionId);
        } catch (Exception e) {
            // Ignore
        }
    }

    @Override
    public void destroy() throws RemoteException {
        // Cleanup if needed
    }
}

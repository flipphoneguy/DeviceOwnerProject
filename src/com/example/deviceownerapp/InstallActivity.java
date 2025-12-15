package com.example.deviceownerapp;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * This is an invisible Activity that handles the "Open with..." intent.
 * It starts the install and then immediately closes itself.
 */
public class InstallActivity extends Activity {

    private static final String TAG = "InstallActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        Uri data = intent.getData();

        if (data != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            String path = data.getPath();
            if (path == null) {
                Logger.log(this, TAG, "Install failed: URI path is null.");
                finish();
                return;
            }

            // Simple check for file extension
            if (path.endsWith(".apk")) {
                Toast.makeText(this, "Installing APK...", Toast.LENGTH_SHORT).show();
                installSingleApk(data);
            } else if (path.endsWith(".xapk") || path.endsWith(".zip")) {
                Toast.makeText(this, "Installing XAPK...", Toast.LENGTH_SHORT).show();
                installXapk(data);
            } else {
                String error = "Unknown file type: " + path;
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                Logger.log(this, TAG, error);
            }
        }

        // Finish immediately
        finish();
    }

    private void installSingleApk(Uri uri) {
        PackageInstaller.Session session = null;
        // Use try-with-resources for the InputStream
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            
            PackageManager pm = getPackageManager();
            PackageInstaller installer = pm.getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            
            int sessionId = installer.createSession(params);
            session = installer.openSession(sessionId);

            // Use try-with-resources for the OutputStream
            try (OutputStream out = session.openWrite("package", 0, -1)) {
                byte[] buffer = new byte[65536];
                int c;
                while ((c = in.read(buffer)) != -1) {
                    out.write(buffer, 0, c);
                }
                session.fsync(out);
            }
            
            Log.d(TAG, "Single APK install session created. Committing.");
            commitSession(session, sessionId);

        } catch (Exception e) {
            String error = "Single APK install failed: " + e.getMessage();
            Logger.log(this, TAG, error);
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            if (session != null) {
                session.abandon();
            }
        }
    }

    private void installXapk(Uri uri) {
        PackageInstaller.Session session = null;
        // Use try-with-resources for the zip and input streams
        try (InputStream in = getContentResolver().openInputStream(uri);
             ZipInputStream zipIn = new ZipInputStream(in)) {
            
            PackageManager pm = getPackageManager();
            PackageInstaller installer = pm.getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);

            int sessionId = installer.createSession(params);
            session = installer.openSession(sessionId);

            ZipEntry entry;
            byte[] buffer = new byte[65536];
            int bytesRead;
            boolean foundApk = false;

            // Loop through every file in the ZIP
            while ((entry = zipIn.getNextEntry()) != null) {
                // We only care about APK files
                if (!entry.isDirectory() && entry.getName().endsWith(".apk")) {
                    foundApk = true;
                    Log.d(TAG, "Found APK in XAPK: " + entry.getName());

                    // Use try-with-resources for the session's OutputStream
                    try (OutputStream out = session.openWrite(entry.getName(), 0, entry.getSize())) {
                        while ((bytesRead = zipIn.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                        session.fsync(out);
                    }
                }
                // Close the entry to move to the next one
                zipIn.closeEntry();
            }

            if (!foundApk) {
                throw new Exception("No .apk files found in the XAPK/ZIP.");
            }

            Log.d(TAG, "XAPK session created. Committing.");
            commitSession(session, sessionId);

        } catch (Exception e) {
            String error = "XAPK install failed: " + e.getMessage();
            Logger.log(this, TAG, error);
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            if (session != null) {
                session.abandon();
            }
        }
    }

    private void commitSession(PackageInstaller.Session session, int sessionId) {
        try {
            // Create a PendingIntent to receive the install result
            Intent intent = new Intent(this, InstallResultReceiver.class);
            int flag = PendingIntent.FLAG_UPDATE_CURRENT | 33554432;
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    this,
                    sessionId,
                    intent,
                    flag
            );

            // Commit the install
            session.commit(pendingIntent.getIntentSender());
            // Session is auto-closed on commit, no need to abandon
            
        } catch (Exception e) {
            String error = "Failed to commit session: " + e.getMessage();
            Logger.log(this, TAG, error);
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            if (session != null) {
                session.abandon();
            }
        }
    }
}

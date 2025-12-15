package com.example.deviceownerapp;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Handles the installation of APKs and XAPKs.
 * Uses a ProgressActivity to show feedback to the user.
 */
public class InstallActivity extends Activity {

    private static final String TAG = "InstallActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        final Uri data = intent.getData();

        if (data != null && (Intent.ACTION_VIEW.equals(intent.getAction()) || Intent.ACTION_SEND.equals(intent.getAction()))) {
            // Start the progress UI
            Intent progressIntent = new Intent(this, ProgressActivity.class);
            progressIntent.putExtra(ProgressActivity.EXTRA_MESSAGE, "Starting installation...");
            // Ensure we start a new task so it sits on top if needed
            progressIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(progressIntent);

            // Execute installation in background to avoid blocking main thread (and ANR)
            new InstallTask(data).execute();
        } else {
            finish();
        }
    }

    private class InstallTask extends AsyncTask<Void, String, String> {
        private Uri uri;
        private PackageInstaller.Session session = null;

        InstallTask(Uri uri) {
            this.uri = uri;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            // Send update to ProgressActivity
            Intent intent = new Intent(ProgressActivity.ACTION_FINISH); // Use same action? No.
            // Actually, we need a way to update the text in ProgressActivity.
            // Since ProgressActivity is simple, we can just finish it and start a new one,
            // OR use a broadcast to update it.
            // Let's assume we don't update text too often to avoid flicker, or just rely on "Processing..."
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                // Determine if it's APK or XAPK/ZIP
                // Since we might not have a file path (content provider), check mime type or try to detect
                String type = getContentResolver().getType(uri);
                String name = "";
                // Try to guess from URI string if name/type is ambiguous
                if (uri.toString().toLowerCase().endsWith(".xapk") || uri.toString().toLowerCase().endsWith(".zip") || "application/zip".equals(type) || "application/octet-stream".equals(type)) {
                    // Treat as XAPK/ZIP
                    return installXapk(uri);
                } else {
                    // Treat as single APK
                    return installSingleApk(uri);
                }
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            // Close the progress activity
            sendBroadcast(new Intent(ProgressActivity.ACTION_FINISH));
            
            if (result != null) {
                // Failure
                ErrorHandler.showError(InstallActivity.this, result);
                Logger.log(InstallActivity.this, TAG, result);
            } else {
                // Success (Session committed). The ResultReceiver will handle the final status toast/log.
                finish();
            }
        }

        private String installSingleApk(Uri uri) {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                PackageManager pm = getPackageManager();
                PackageInstaller installer = pm.getPackageInstaller();
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL);

                int sessionId = installer.createSession(params);
                session = installer.openSession(sessionId);

                try (OutputStream out = session.openWrite("package", 0, -1)) {
                    byte[] buffer = new byte[65536];
                    int c;
                    while ((c = in.read(buffer)) != -1) {
                        out.write(buffer, 0, c);
                    }
                    session.fsync(out);
                }

                commitSession(session, sessionId);
                return null; // Success

            } catch (Exception e) {
                if (session != null) session.abandon();
                return "Single APK install failed: " + e.getMessage();
            }
        }

        private String installXapk(Uri uri) {
            try (InputStream in = getContentResolver().openInputStream(uri);
                 ZipInputStream zipIn = new ZipInputStream(in)) {

                PackageManager pm = getPackageManager();
                PackageInstaller installer = pm.getPackageInstaller();
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL);

                // Note: If we knew the total size, we could set it.
                // Multi-package install is implicit with Session.

                int sessionId = installer.createSession(params);
                session = installer.openSession(sessionId);

                ZipEntry entry;
                byte[] buffer = new byte[65536];
                int bytesRead;
                boolean foundApk = false;

                while ((entry = zipIn.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    if (!entry.isDirectory() && entryName.toLowerCase().endsWith(".apk")) {
                        foundApk = true;
                        Log.d(TAG, "Processing APK: " + entryName);

                        // Sanitize entry name for session stream name
                        String sessionName = new File(entryName).getName();

                        try (OutputStream out = session.openWrite(sessionName, 0, entry.getSize() > 0 ? entry.getSize() : -1)) {
                            while ((bytesRead = zipIn.read(buffer)) != -1) {
                                out.write(buffer, 0, bytesRead);
                            }
                            session.fsync(out);
                        }
                    }
                    // FUTURE: Handle OBBs by copying to /Android/obb/
                    zipIn.closeEntry();
                }

                if (!foundApk) {
                    throw new Exception("No .apk files found inside the XAPK/ZIP.");
                }

                commitSession(session, sessionId);
                return null; // Success

            } catch (Exception e) {
                if (session != null) session.abandon();
                return "XAPK install failed: " + e.getMessage();
            }
        }

        private void commitSession(PackageInstaller.Session session, int sessionId) throws IOException {
            Intent intent = new Intent(InstallActivity.this, InstallResultReceiver.class);
            // FLAG_IMMUTABLE (67108864) or FLAG_MUTABLE (33554432) depending on target SDK.
            // Using 33554432 (FLAG_MUTABLE) for compatibility with older Android or broadcast receiver requirements
            int flag = PendingIntent.FLAG_UPDATE_CURRENT | 33554432;
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    InstallActivity.this,
                    sessionId,
                    intent,
                    flag
            );
            session.commit(pendingIntent.getIntentSender());
        }
    }
}

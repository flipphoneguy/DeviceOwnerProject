package com.example.deviceownerapp;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class InstallActivity extends Activity {

    private static final String TAG = "InstallActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        final Uri data = intent.getData();

        if (data != null && (Intent.ACTION_VIEW.equals(intent.getAction()) || Intent.ACTION_SEND.equals(intent.getAction()))) {
            Intent progressIntent = new Intent(this, ProgressActivity.class);
            progressIntent.putExtra(ProgressActivity.EXTRA_MESSAGE, "Starting installation...");
            progressIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(progressIntent);

            new InstallTask(data).execute();
        } else {
            finish();
        }
    }

    private class InstallTask extends AsyncTask<Void, String, String> {
        private Uri uri;
        private PackageInstaller.Session session = null;
        private File tempFile = null;

        InstallTask(Uri uri) {
            this.uri = uri;
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                String type = getContentResolver().getType(uri);
                if (uri.toString().toLowerCase().endsWith(".xapk") ||
                    uri.toString().toLowerCase().endsWith(".zip") ||
                    "application/zip".equals(type) ||
                    "application/octet-stream".equals(type)) {
                    return installXapk(uri);
                } else {
                    return installSingleApk(uri);
                }
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            } finally {
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete();
                }
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                // Send error to ProgressActivity
                Intent errorIntent = new Intent(InstallActivity.this, ProgressActivity.class);
                errorIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                errorIntent.putExtra(ProgressActivity.EXTRA_ERROR, result);
                startActivity(errorIntent);
            }
            // If success, InstallResultReceiver handles it.
            finish();
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
                return null;

            } catch (Exception e) {
                if (session != null) session.abandon();
                return "Single APK install failed: " + e.getMessage();
            }
        }

        private String installXapk(Uri uri) {
            ZipFile zipFile = null;
            try {
                // Copy URI to temp file to support ZipFile (random access)
                tempFile = File.createTempFile("install", ".xapk", getCacheDir());
                try (InputStream in = getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[65536];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }

                zipFile = new ZipFile(tempFile);

                PackageManager pm = getPackageManager();
                PackageInstaller installer = pm.getPackageInstaller();
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL);

                int sessionId = installer.createSession(params);
                session = installer.openSession(sessionId);

                byte[] buffer = new byte[65536];
                boolean foundApk = false;

                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String entryName = entry.getName();
                    if (!entry.isDirectory() && entryName.toLowerCase().endsWith(".apk")) {
                        foundApk = true;
                        String sessionName = new File(entryName).getName();

                        try (InputStream in = zipFile.getInputStream(entry);
                             OutputStream out = session.openWrite(sessionName, 0, entry.getSize())) {
                            int bytesRead;
                            while ((bytesRead = in.read(buffer)) != -1) {
                                out.write(buffer, 0, bytesRead);
                            }
                            session.fsync(out);
                        }
                    }
                }

                if (!foundApk) {
                    throw new Exception("No .apk files found inside the XAPK/ZIP.");
                }

                commitSession(session, sessionId);
                return null;

            } catch (Exception e) {
                if (session != null) session.abandon();
                return "XAPK install failed: " + e.getMessage();
            } finally {
                if (zipFile != null) {
                    try { zipFile.close(); } catch (IOException ignored) {}
                }
            }
        }

        private void commitSession(PackageInstaller.Session session, int sessionId) throws IOException {
            Intent intent = new Intent(InstallActivity.this, InstallResultReceiver.class);
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

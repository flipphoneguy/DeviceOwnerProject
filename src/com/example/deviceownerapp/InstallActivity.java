package com.example.deviceownerapp;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
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
            progressIntent.putExtra(ProgressActivity.EXTRA_MESSAGE, "Installing...");
            progressIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(progressIntent);

            // Check mode and route to appropriate install method
            DpmHelper.Mode mode = DpmHelper.getActiveMode(this);
            if (mode == DpmHelper.Mode.DHIZUKU) {
                // Use full Dhizuku binder wrapping for silent install
                new DhizukuBinderInstallTask(data).execute();
            } else {
                // Use native install (shows UI if not Device Owner)
                new NativeInstallTask(data).execute();
            }
        } else {
            finish();
        }
    }

    // ======== Dhizuku Binder Installation ========

    /**
     * Install task that uses Dhizuku's wrapped PackageInstaller binder for silent installation.
     * All PackageInstaller operations (create session, write, commit) go through Dhizuku's UID.
     */
    private class DhizukuBinderInstallTask extends AsyncTask<Void, String, String> {
        private Uri uri;
        private File tempFile = null;

        DhizukuBinderInstallTask(Uri uri) {
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
                    return installXapkDhizukuBinder(uri);
                } else {
                    return installSingleApkDhizukuBinder(uri);
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
                showError(result);
            }
            // Don't show "completed" here - commit() is async
            // InstallResultReceiver will handle the actual result
            // ProgressActivity is already showing "Starting installation..."
            finish();
        }

        private String installSingleApkDhizukuBinder(Uri uri) {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                DpmHelper.DhizukuInstallResult result = DpmHelper.installApkThroughDhizuku(
                        InstallActivity.this, in, "base.apk");
                if (!result.success) {
                    return "Dhizuku APK install failed: " + result.error;
                }
                return null;
            } catch (Exception e) {
                return "Dhizuku APK install failed: " + e.getMessage();
            }
        }

        private String installXapkDhizukuBinder(Uri uri) {
            ZipFile zipFile = null;
            try {
                // Copy to temp file for ZipFile
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

                // TODO: For XAPK with multiple APKs, we need to install them all in one session
                // For now, find and install only the base APK
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                ZipEntry baseApk = null;
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String entryName = entry.getName().toLowerCase();
                    if (!entry.isDirectory() && entryName.endsWith(".apk")) {
                        if (entryName.contains("base") || baseApk == null) {
                            baseApk = entry;
                            if (entryName.contains("base")) break;
                        }
                    }
                }

                if (baseApk == null) {
                    return "No APK files found in XAPK";
                }

                try (InputStream in = zipFile.getInputStream(baseApk)) {
                    DpmHelper.DhizukuInstallResult result = DpmHelper.installApkThroughDhizuku(
                            InstallActivity.this, in, "base.apk");
                    if (!result.success) {
                        return "Dhizuku XAPK install failed: " + result.error;
                    }
                    return null;
                }

            } catch (Exception e) {
                return "Dhizuku XAPK install failed: " + e.getMessage();
            } finally {
                if (zipFile != null) {
                    try { zipFile.close(); } catch (IOException ignored) {}
                }
            }
        }
    }

    // ======== Dhizuku UserService Installation (deprecated - not working) ========

    private void startDhizukuInstall(final Uri uri) {
        DpmHelper.bindInstallService(this, new DpmHelper.InstallServiceCallback() {
            @Override
            public void onServiceConnected(IDhizukuInstallService service) {
                new DhizukuInstallTask(uri, service).execute();
            }

            @Override
            public void onServiceDisconnected() {
                showError("Dhizuku service disconnected");
                finish();
            }

            @Override
            public void onBindingFailed(String error) {
                // Fall back to native install
                Logger.log(InstallActivity.this, TAG, "Dhizuku binding failed, using native: " + error);
                new NativeInstallTask(uri).execute();
            }
        });
    }

    private class DhizukuInstallTask extends AsyncTask<Void, String, String> {
        private Uri uri;
        private IDhizukuInstallService service;
        private File tempFile = null;
        private int sessionId = -1;

        DhizukuInstallTask(Uri uri, IDhizukuInstallService service) {
            this.uri = uri;
            this.service = service;
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                String type = getContentResolver().getType(uri);
                if (uri.toString().toLowerCase().endsWith(".xapk") ||
                    uri.toString().toLowerCase().endsWith(".zip") ||
                    "application/zip".equals(type) ||
                    "application/octet-stream".equals(type)) {
                    return installXapkDhizuku(uri);
                } else {
                    return installSingleApkDhizuku(uri);
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
                showError(result);
            }
            finish();
        }

        private String installSingleApkDhizuku(Uri uri) {
            try {
                // Copy to temp file for ParcelFileDescriptor
                tempFile = File.createTempFile("install", ".apk", getCacheDir());
                try (InputStream in = getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[65536];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }

                // Create session through Dhizuku
                sessionId = service.createInstallSession();

                // Write APK through Dhizuku
                ParcelFileDescriptor pfd = ParcelFileDescriptor.open(tempFile,
                        ParcelFileDescriptor.MODE_READ_ONLY);
                service.writeToSession(sessionId, "base.apk", pfd);

                // Commit through Dhizuku
                service.commitSession(sessionId);
                return null;

            } catch (Exception e) {
                if (sessionId >= 0) {
                    try { service.abandonSession(sessionId); } catch (Exception ignored) {}
                }
                return "Dhizuku APK install failed: " + e.getMessage();
            }
        }

        private String installXapkDhizuku(Uri uri) {
            ZipFile zipFile = null;
            List<File> apkFiles = new ArrayList<>();
            try {
                // Copy to temp file for ZipFile
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

                // Extract APKs to temp files
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String entryName = entry.getName();
                    if (!entry.isDirectory() && entryName.toLowerCase().endsWith(".apk")) {
                        File apkFile = File.createTempFile("split", ".apk", getCacheDir());
                        try (InputStream in = zipFile.getInputStream(entry);
                             FileOutputStream out = new FileOutputStream(apkFile)) {
                            byte[] buffer = new byte[65536];
                            int read;
                            while ((read = in.read(buffer)) != -1) {
                                out.write(buffer, 0, read);
                            }
                        }
                        apkFiles.add(apkFile);
                    }
                }

                if (apkFiles.isEmpty()) {
                    throw new Exception("No .apk files found inside the XAPK/ZIP.");
                }

                // Create session through Dhizuku
                sessionId = service.createInstallSession();

                // Write each APK through Dhizuku
                for (int i = 0; i < apkFiles.size(); i++) {
                    File apkFile = apkFiles.get(i);
                    ParcelFileDescriptor pfd = ParcelFileDescriptor.open(apkFile,
                            ParcelFileDescriptor.MODE_READ_ONLY);
                    String name = (i == 0) ? "base.apk" : "split_" + i + ".apk";
                    service.writeToSession(sessionId, name, pfd);
                }

                // Commit through Dhizuku
                service.commitSession(sessionId);
                return null;

            } catch (Exception e) {
                if (sessionId >= 0) {
                    try { service.abandonSession(sessionId); } catch (Exception ignored) {}
                }
                return "Dhizuku XAPK install failed: " + e.getMessage();
            } finally {
                if (zipFile != null) {
                    try { zipFile.close(); } catch (IOException ignored) {}
                }
                // Cleanup temp APK files
                for (File f : apkFiles) {
                    f.delete();
                }
            }
        }
    }

    // ======== Native Installation (for non-Dhizuku mode) ========

    private class NativeInstallTask extends AsyncTask<Void, String, String> {
        private Uri uri;
        private PackageInstaller.Session session = null;
        private File tempFile = null;

        NativeInstallTask(Uri uri) {
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
                showError(result);
            }
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
            // Use DpmHelper to commit - it will use Dhizuku binder wrapper if in Dhizuku mode
            DpmHelper.commitSession(InstallActivity.this, session, sessionId, intent);
        }
    }

    private void showError(String message) {
        Intent errorIntent = new Intent(this, ProgressActivity.class);
        errorIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        errorIntent.putExtra(ProgressActivity.EXTRA_ERROR, message);
        startActivity(errorIntent);
    }
}

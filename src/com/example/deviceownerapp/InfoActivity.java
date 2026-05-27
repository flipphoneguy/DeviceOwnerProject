package com.example.deviceownerapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InfoActivity extends Activity {

    private static final String TAG = "InfoActivity";
    private static final String CONTACT_URL = "https://flipphoneguy.duckdns.org/contact";
    private static final String UPDATE_FILE = "update.apk";

    private Button btnUpdate;
    private Button btnTheme;
    private TextView updateStatus;

    private final String[] themeLabels = new String[3];

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(ThemeHelper.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);

        themeLabels[ThemeHelper.MODE_SYSTEM] = getString(R.string.theme_system);
        themeLabels[ThemeHelper.MODE_LIGHT] = getString(R.string.theme_light);
        themeLabels[ThemeHelper.MODE_DARK] = getString(R.string.theme_dark);

        ((TextView) findViewById(R.id.info_app_name)).setText(BuildConfig.APP_NAME);
        ((TextView) findViewById(R.id.info_version)).setText("v" + BuildConfig.VERSION_NAME);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        btnUpdate = (Button) findViewById(R.id.btn_check_update);
        updateStatus = (TextView) findViewById(R.id.update_status);
        btnUpdate.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { checkForUpdate(); }
        });

        final String repoName = BuildConfig.REPO.contains("/")
                ? BuildConfig.REPO.split("/")[1] : BuildConfig.REPO;

        findViewById(R.id.btn_github_profile).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openUrl(BuildConfig.GITHUB_PROFILE); }
        });
        findViewById(R.id.btn_app_repo).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                openUrl(BuildConfig.GITHUB_PROFILE + "/" + repoName);
            }
        });
        findViewById(R.id.btn_contact).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openUrl(CONTACT_URL); }
        });

        btnTheme = (Button) findViewById(R.id.btn_theme);
        btnTheme.setText(themeLabels[ThemeHelper.getMode(this)]);
        btnTheme.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int next = (ThemeHelper.getMode(InfoActivity.this) + 1) % 3;
                ThemeHelper.setMode(InfoActivity.this, next);
                recreate();
            }
        });

        findViewById(R.id.btn_uninstall).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmUninstall(); }
        });
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "No browser available", Toast.LENGTH_SHORT).show();
        }
    }

    private void setStatus(String text) {
        updateStatus.setVisibility(View.VISIBLE);
        updateStatus.setText(text);
    }

    private void checkForUpdate() {
        btnUpdate.setEnabled(false);
        setStatus(getString(R.string.update_checking));

        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final Release release = fetchLatest();
                    if (release == null || release.apkUrl == null || release.tag == null) {
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                setStatus(getString(R.string.update_no_release));
                                btnUpdate.setEnabled(true);
                            }
                        });
                        return;
                    }
                    int cmp = compareVersions(release.tag, BuildConfig.VERSION_NAME);
                    if (cmp <= 0) {
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                setStatus(getString(R.string.update_up_to_date,
                                        BuildConfig.VERSION_NAME));
                                btnUpdate.setEnabled(true);
                            }
                        });
                        return;
                    }
                    runOnUiThread(new Runnable() {
                        @Override public void run() { showUpdatePrompt(release); }
                    });
                } catch (final Exception e) {
                    Logger.log(InfoActivity.this, TAG, "Update check failed: " + e.getMessage());
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            setStatus(getString(R.string.update_download_failed,
                                    String.valueOf(e.getMessage())));
                            btnUpdate.setEnabled(true);
                        }
                    });
                }
            }
        }).start();
    }

    private void showUpdatePrompt(final Release release) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.update_available_title)
                .setMessage(getString(R.string.update_available_msg, release.tag))
                .setPositiveButton(R.string.btn_download, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        downloadAndInstall(release);
                    }
                })
                .setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        btnUpdate.setEnabled(true);
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override public void onCancel(DialogInterface d) {
                        btnUpdate.setEnabled(true);
                    }
                })
                .show();
    }

    private void downloadAndInstall(final Release release) {
        setStatus(getString(R.string.update_downloading, release.tag));
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final File apk = downloadApk(release.apkUrl);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            setStatus(getString(R.string.update_installing));
                            launchInstall(apk);
                            btnUpdate.setEnabled(true);
                        }
                    });
                } catch (final Exception e) {
                    Logger.log(InfoActivity.this, TAG, "Update download failed: " + e.getMessage());
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            setStatus(getString(R.string.update_download_failed,
                                    String.valueOf(e.getMessage())));
                            btnUpdate.setEnabled(true);
                        }
                    });
                }
            }
        }).start();
    }

    private void launchInstall(File apk) {
        Uri uri = Uri.parse("content://" + getPackageName() + ".fileprovider/" + apk.getName());
        Intent i = new Intent(this, InstallActivity.class);
        i.setAction(Intent.ACTION_VIEW);
        i.setData(uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(i);
    }

    private void confirmUninstall() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.btn_uninstall)
                .setMessage(R.string.btn_uninstall)
                .setPositiveButton(R.string.btn_yes, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        doUninstall();
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void doUninstall() {
        try {
            DpmHelper.Mode mode = DpmHelper.getActiveMode(this);
            if (mode == DpmHelper.Mode.NATIVE_OWNER) {
                Toast.makeText(this, R.string.uninstall_clearing, Toast.LENGTH_SHORT).show();
                DpmHelper.clearDeviceOwner(this);
            }
            Toast.makeText(this, R.string.uninstall_removing_admin, Toast.LENGTH_SHORT).show();
            DpmHelper.removeActiveAdmin(this);

            Uri pkgUri = Uri.parse("package:" + getPackageName());
            startActivity(new Intent(Intent.ACTION_DELETE, pkgUri));
        } catch (Exception e) {
            Logger.log(this, TAG, "Uninstall error: " + e.getMessage());
            ErrorHandler.showError(this, "Error removing admin: " + e.getMessage());
        }
    }

    private static class Release {
        String tag;
        String apkUrl;
    }

    private Release fetchLatest() throws IOException {
        String body = fetchApi("https://api.github.com/repos/" + BuildConfig.REPO + "/releases/latest");
        Matcher tagM = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        Matcher urlM = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.apk)\"").matcher(body);
        Release r = new Release();
        if (tagM.find()) r.tag = tagM.group(1);
        if (urlM.find()) r.apkUrl = urlM.group(1);
        return r;
    }

    private String fetchApi(String apiUrl) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(apiUrl).openConnection();
        c.setRequestProperty("User-Agent", "DeviceOwnerApp/" + BuildConfig.VERSION_NAME);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("GitHub API HTTP " + code);
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            in.close();
            return bos.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    private File downloadApk(String apkUrl) throws Exception {
        File out = new File(getExternalCacheDir(), UPDATE_FILE);
        HttpURLConnection conn = (HttpURLConnection) new URL(apkUrl).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setInstanceFollowRedirects(true);
        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
        } finally {
            conn.disconnect();
        }
        return out;
    }

    private static int compareVersions(String a, String b) {
        String ca = a == null ? "" : a.replaceFirst("^[vV]", "");
        String cb = b == null ? "" : b.replaceFirst("^[vV]", "");
        String[] pa = ca.split("\\.");
        String[] pb = cb.split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int va = i < pa.length ? parseInt(pa[i]) : 0;
            int vb = i < pb.length ? parseInt(pb[i]) : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }
}

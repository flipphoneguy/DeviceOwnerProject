package com.example.deviceownerapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_PICK_FILE = 1001;

    private static final int FILTER_USER = 0;
    private static final int FILTER_SYSTEM = 1;
    private static final int FILTER_ALL = 2;

    private TextView statusText;
    private TextView permWarning;
    private TextView appsEmpty;
    private Button connectDhizukuBtn;
    private Button filterUserBtn, filterSystemBtn, filterAllBtn;
    private EditText searchBox;
    private LinearLayout appListContainer;
    private PackageManager pm;
    private final List<ApplicationInfo> allApps = new ArrayList<>();
    private final List<ApplicationInfo> visibleApps = new ArrayList<>();
    private int appliedThemeMode = -1;
    private int currentFilter = FILTER_USER;
    private String searchQuery = "";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(ThemeHelper.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appliedThemeMode = ThemeHelper.getMode(this);
        setContentView(R.layout.activity_main);

        pm = getPackageManager();
        statusText = (TextView) findViewById(R.id.status_text);
        permWarning = (TextView) findViewById(R.id.perm_warning);
        appsEmpty = (TextView) findViewById(R.id.apps_empty);
        connectDhizukuBtn = (Button) findViewById(R.id.btn_connect_dhizuku);
        appListContainer = (LinearLayout) findViewById(R.id.app_list_container);
        searchBox = (EditText) findViewById(R.id.search_box);
        filterUserBtn = (Button) findViewById(R.id.filter_user);
        filterSystemBtn = (Button) findViewById(R.id.filter_system);
        filterAllBtn = (Button) findViewById(R.id.filter_all);

        findViewById(R.id.btn_info).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, InfoActivity.class));
            }
        });

        findViewById(R.id.btn_install_file).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openFilePicker(); }
        });

        connectDhizukuBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { connectDhizuku(); }
        });

        statusText.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSetupInstructions(); }
        });

        permWarning.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showInstallPermissionPrompt(); }
        });

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                searchQuery = s.toString().trim().toLowerCase(Locale.getDefault());
                applyFilter();
            }
        });

        filterUserBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setFilter(FILTER_USER); }
        });
        filterSystemBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setFilter(FILTER_SYSTEM); }
        });
        filterAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setFilter(FILTER_ALL); }
        });

        updateFilterButtonStyles();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appliedThemeMode != ThemeHelper.getMode(this)) {
            recreate();
            return;
        }
        updateStatus();
        loadApps();
    }

    private void updateStatus() {
        DpmHelper.Mode mode = DpmHelper.getActiveMode(this);
        boolean canInstall = canRequestInstalls();
        switch (mode) {
            case NATIVE_OWNER:
                statusText.setText(R.string.status_native_owner);
                statusText.setTextColor(getColor(R.color.status_ok));
                setStatusClickable(false);
                connectDhizukuBtn.setVisibility(View.GONE);
                break;
            case DHIZUKU:
                statusText.setText(R.string.status_dhizuku_connected);
                statusText.setTextColor(getColor(R.color.status_info));
                setStatusClickable(false);
                connectDhizukuBtn.setVisibility(View.GONE);
                break;
            case NONE:
            default:
                statusText.setText(R.string.status_no_privileges_action);
                statusText.setTextColor(getColor(R.color.status_warn));
                setStatusClickable(true);
                connectDhizukuBtn.setVisibility(
                        DpmHelper.isDhizukuInstalled(this) ? View.VISIBLE : View.GONE);
                break;
        }

        // Install-from-unknown permission: only matters when not Device Owner / not Dhizuku.
        if (mode == DpmHelper.Mode.NONE && !canInstall) {
            permWarning.setText(R.string.perm_install_missing);
            permWarning.setVisibility(View.VISIBLE);
            permWarning.setFocusable(true);
            permWarning.setClickable(true);
            permWarning.setBackgroundResource(R.drawable.list_item_bg);
        } else {
            permWarning.setVisibility(View.GONE);
            permWarning.setFocusable(false);
            permWarning.setClickable(false);
        }
    }

    private void setStatusClickable(boolean clickable) {
        if (clickable) {
            statusText.setFocusable(true);
            statusText.setFocusableInTouchMode(false);
            statusText.setClickable(true);
            statusText.setBackgroundResource(R.drawable.list_item_bg);
        } else {
            statusText.setFocusable(false);
            statusText.setClickable(false);
            statusText.setBackground(null);
        }
    }

    private boolean canRequestInstalls() {
        if (Build.VERSION.SDK_INT < 26) return true;
        try { return pm.canRequestPackageInstalls(); }
        catch (Exception e) { return false; }
    }

    private void showSetupInstructions() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.setup_title)
                .setMessage(R.string.setup_instructions)
                .setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { d.dismiss(); }
                })
                .show();
    }

    private void showInstallPermissionPrompt() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.perm_install_title)
                .setMessage(R.string.perm_install_msg)
                .setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        openInstallPermissionSettings();
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void openInstallPermissionSettings() {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } else {
                startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
            }
        } catch (Exception e) {
            Logger.log(this, TAG, "openInstallPermissionSettings failed: " + e.getMessage());
            Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show();
        }
    }

    private void connectDhizuku() {
        if (!DpmHelper.isDhizukuInstalled(this)) {
            Toast.makeText(this, R.string.dhizuku_not_available, Toast.LENGTH_SHORT).show();
            return;
        }
        DpmHelper.requestDhizukuPermission(this, new DpmHelper.PermissionCallback() {
            @Override public void onResult(boolean granted) {
                Toast.makeText(MainActivity.this,
                        granted ? R.string.dhizuku_permission_granted
                                : R.string.dhizuku_permission_denied,
                        Toast.LENGTH_SHORT).show();
                updateStatus();
                loadApps();
            }
        });
    }

    private void loadApps() {
        List<ApplicationInfo> list = pm.getInstalledApplications(
                PackageManager.MATCH_UNINSTALLED_PACKAGES);
        Collections.sort(list, new Comparator<ApplicationInfo>() {
            @Override public int compare(ApplicationInfo a, ApplicationInfo b) {
                try {
                    return a.loadLabel(pm).toString()
                            .compareToIgnoreCase(b.loadLabel(pm).toString());
                } catch (Exception e) { return 0; }
            }
        });
        allApps.clear();
        allApps.addAll(list);
        applyFilter();
    }

    private void setFilter(int filter) {
        if (currentFilter == filter) return;
        currentFilter = filter;
        updateFilterButtonStyles();
        applyFilter();
    }

    private void updateFilterButtonStyles() {
        filterUserBtn.setTypeface(null, currentFilter == FILTER_USER
                ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        filterSystemBtn.setTypeface(null, currentFilter == FILTER_SYSTEM
                ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        filterAllBtn.setTypeface(null, currentFilter == FILTER_ALL
                ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    private void applyFilter() {
        visibleApps.clear();
        for (ApplicationInfo app : allApps) {
            boolean isSystem = (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (currentFilter == FILTER_USER && isSystem) continue;
            if (currentFilter == FILTER_SYSTEM && !isSystem) continue;

            if (!searchQuery.isEmpty()) {
                String name = "";
                try { name = app.loadLabel(pm).toString().toLowerCase(Locale.getDefault()); }
                catch (Exception ignored) {}
                String pkg = app.packageName == null
                        ? "" : app.packageName.toLowerCase(Locale.getDefault());
                if (!name.contains(searchQuery) && !pkg.contains(searchQuery)) continue;
            }
            visibleApps.add(app);
        }
        renderAppList();
    }

    private void renderAppList() {
        appListContainer.removeAllViews();
        if (visibleApps.isEmpty()) {
            appsEmpty.setText(R.string.apps_empty);
            appsEmpty.setVisibility(View.VISIBLE);
            appListContainer.setVisibility(View.GONE);
            return;
        }
        appsEmpty.setVisibility(View.GONE);
        appListContainer.setVisibility(View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (final ApplicationInfo app : visibleApps) {
            TextView tv = (TextView) inflater.inflate(
                    R.layout.list_item_app, appListContainer, false);
            String name = app.loadLabel(pm).toString();
            if (DpmHelper.isApplicationHidden(this, app.packageName)) {
                name = name + getString(R.string.hidden_suffix);
            }
            tv.setText(name);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Intent i = new Intent(MainActivity.this, AppDetailActivity.class);
                    i.putExtra("packageName", app.packageName);
                    startActivity(i);
                }
            });
            appListContainer.addView(tv);
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {
                "application/vnd.android.package-archive",
                "application/zip",
                "application/octet-stream"
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        try {
            startActivityForResult(Intent.createChooser(intent, "Select File"), REQUEST_PICK_FILE);
        } catch (Exception e) {
            ErrorHandler.showError(this, "Could not open file picker: " + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_FILE && resultCode == Activity.RESULT_OK
                && data != null && data.getData() != null) {
            Uri uri = data.getData();
            Intent installIntent = new Intent(this, InstallActivity.class);
            installIntent.setAction(Intent.ACTION_VIEW);
            installIntent.setData(uri);
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(installIntent);
        }
    }
}

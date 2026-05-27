package com.example.deviceownerapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class AppDetailActivity extends Activity {

    private static final String TAG = "AppDetail";

    private PackageManager pm;
    private String packageName;
    private Switch hideSwitch;
    private LinearLayout permissionContainer;
    private boolean suppressHideChange = false;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(ThemeHelper.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_detail);

        packageName = getIntent().getStringExtra("packageName");
        if (packageName == null) {
            finish();
            return;
        }

        pm = getPackageManager();
        hideSwitch = (Switch) findViewById(R.id.hide_switch);
        permissionContainer = (LinearLayout) findViewById(R.id.permission_container);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        loadAppInfo();
        setupHideSwitch();
        loadPermissions();
    }

    private void loadAppInfo() {
        TextView nameView = (TextView) findViewById(R.id.app_name_text);
        TextView pkgView = (TextView) findViewById(R.id.app_pkg_text);
        try {
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            nameView.setText(info.loadLabel(pm));
            pkgView.setText(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            nameView.setText(packageName);
            pkgView.setText("");
        }
    }

    private void setupHideSwitch() {
        DpmHelper.Mode mode = DpmHelper.getActiveMode(this);
        if (mode == DpmHelper.Mode.NONE) {
            hideSwitch.setEnabled(false);
        } else {
            suppressHideChange = true;
            hideSwitch.setChecked(DpmHelper.isApplicationHidden(this, packageName));
            suppressHideChange = false;
        }
        hideSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(final CompoundButton btn, final boolean checked) {
                if (suppressHideChange) return;
                if (checked) {
                    new AlertDialog.Builder(AppDetailActivity.this)
                            .setTitle(R.string.hide_warning_title)
                            .setMessage(R.string.hide_warning_msg)
                            .setPositiveButton(R.string.btn_yes, new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface d, int which) {
                                    applyHidden(true, btn);
                                }
                            })
                            .setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface d, int which) {
                                    suppressHideChange = true;
                                    btn.setChecked(false);
                                    suppressHideChange = false;
                                }
                            })
                            .setCancelable(false)
                            .show();
                } else {
                    applyHidden(false, btn);
                }
            }
        });
    }

    private void applyHidden(boolean hidden, CompoundButton btn) {
        boolean ok = DpmHelper.setApplicationHidden(this, packageName, hidden);
        if (ok) {
            Toast.makeText(this, hidden ? "App hidden" : "App unhidden",
                    Toast.LENGTH_SHORT).show();
        } else {
            Logger.log(this, TAG, "Failed to change hidden state");
            suppressHideChange = true;
            btn.setChecked(!hidden);
            suppressHideChange = false;
        }
    }

    @SuppressWarnings("deprecation")
    private void loadPermissions() {
        permissionContainer.removeAllViews();
        try {
            PackageInfo info = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
            String[] perms = info.requestedPermissions;
            if (perms == null || perms.length == 0) {
                TextView none = new TextView(this);
                none.setText(R.string.no_permissions);
                none.setTextColor(getColor(R.color.text_secondary));
                none.setTextSize(13);
                permissionContainer.addView(none);
                return;
            }
            DpmHelper.Mode mode = DpmHelper.getActiveMode(this);
            for (final String permission : perms) {
                boolean isRuntime = false;
                try {
                    PermissionInfo pInfo = pm.getPermissionInfo(permission, 0);
                    int level = pInfo.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE;
                    isRuntime = (level == PermissionInfo.PROTECTION_DANGEROUS);
                } catch (PackageManager.NameNotFoundException ignored) {}

                Switch sw = new Switch(this);
                final String shortName = permission.contains(".")
                        ? permission.substring(permission.lastIndexOf('.') + 1)
                        : permission;
                sw.setText(shortName);
                sw.setTextColor(getColor(R.color.text_primary));
                sw.setTextSize(13);
                sw.setMinHeight(40);
                sw.setFocusable(true);

                if (!isRuntime) {
                    sw.setChecked(true);
                    sw.setEnabled(false);
                } else if (mode == DpmHelper.Mode.NONE) {
                    sw.setEnabled(false);
                } else {
                    int state = DpmHelper.getPermissionGrantState(this, packageName, permission);
                    sw.setChecked(state == DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
                    sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton btn, boolean checked) {
                            int newState = checked
                                    ? DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                                    : DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED;
                            boolean ok = DpmHelper.setPermissionGrantState(
                                    AppDetailActivity.this, packageName, permission, newState);
                            if (ok) {
                                Toast.makeText(AppDetailActivity.this,
                                        shortName + (checked ? " granted" : " denied"),
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                btn.setChecked(!checked);
                            }
                        }
                    });
                }
                permissionContainer.addView(sw);
            }
        } catch (Exception e) {
            Logger.log(this, TAG, "Could not load permissions: " + e.getMessage());
        }
    }
}

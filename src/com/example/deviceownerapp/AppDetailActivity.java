package com.example.deviceownerapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class AppDetailActivity extends Activity {

    private static final String TAG = "AppDetailActivity";

    private DevicePolicyManager dpm;
    private PackageManager pm;
    private ComponentName adminComponent;
    private String packageName;

    private TextView appNameText;
    private Switch hideSwitch;
    private LinearLayout permissionContainer;
    private boolean isProgrammaticChange = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_detail_layout);

        packageName = getIntent().getStringExtra("packageName");
        if (packageName == null) {
            finish();
            return;
        }

        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        pm = getPackageManager();
        adminComponent = new ComponentName(this, DeviceAdmin.class);

        appNameText = findViewById(R.id.app_name_text);
        hideSwitch = findViewById(R.id.hide_switch);
        permissionContainer = findViewById(R.id.permission_container);

        loadAppInfo();
        loadPermissions();
        setupHideSwitch();
    }

    private void loadAppInfo() {
        try {
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            appNameText.setText(appInfo.loadLabel(pm));
        } catch (PackageManager.NameNotFoundException e) {
            appNameText.setText("Error: App not found");
        }
    }

    private void setupHideSwitch() {
        try {
            boolean isHidden = dpm.isApplicationHidden(adminComponent, packageName);
            isProgrammaticChange = true;
            hideSwitch.setChecked(isHidden);
            isProgrammaticChange = false;
        } catch (SecurityException e) {
            hideSwitch.setEnabled(false);
        }

        hideSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
                if (isProgrammaticChange) return;

                if (isChecked) {
                    // Show warning before hiding
                    new AlertDialog.Builder(AppDetailActivity.this)
                        .setTitle("Warning")
                        .setMessage("Hiding this app may cause it to disappear from the applications menu indefinitely, even if unhidden later, due to Sonim launcher behavior. Are you sure?")
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                setAppHidden(true, buttonView);
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                isProgrammaticChange = true;
                                buttonView.setChecked(false);
                                isProgrammaticChange = false;
                            }
                        })
                        .setCancelable(false)
                        .show();
                } else {
                    setAppHidden(false, buttonView);
                }
            }
        });
    }

    private void setAppHidden(boolean hidden, CompoundButton buttonView) {
        try {
            dpm.setApplicationHidden(adminComponent, packageName, hidden);
            String status = hidden ? "hidden" : "unhidden";
            Toast.makeText(AppDetailActivity.this, "App " + status, Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Logger.log(AppDetailActivity.this, TAG, "Failed to change hidden state: " + e.getMessage());
            isProgrammaticChange = true;
            buttonView.setChecked(!hidden);
            isProgrammaticChange = false;
        }
    }

    private void loadPermissions() {
        try {
            PackageInfo pkgInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
            String[] permissions = pkgInfo.requestedPermissions;

            if (permissions == null || permissions.length == 0) {
                TextView noPerms = new TextView(this);
                noPerms.setText("No permissions requested.");
                permissionContainer.addView(noPerms);
                return;
            }

            for (final String permission : permissions) {
                boolean isRuntime = false;
                try {
                    PermissionInfo pInfo = pm.getPermissionInfo(permission, 0);
                    @SuppressWarnings("deprecation")
                    int protectionLevel = pInfo.protectionLevel;
                    if (protectionLevel == PermissionInfo.PROTECTION_DANGEROUS) {
                        isRuntime = true;
                    }
                } catch (PackageManager.NameNotFoundException e) {
                }

                Switch permSwitch = new Switch(this);
                final String shortName = permission.substring(permission.lastIndexOf(".") + 1);
                permSwitch.setText(shortName);
                permSwitch.setMinHeight(120);

                if (isRuntime) {
                    int grantState = dpm.getPermissionGrantState(adminComponent, packageName, permission);
                    permSwitch.setChecked(grantState == DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);

                    permSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            try {
                                int newState = isChecked ?
                                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED :
                                        DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED;

                                dpm.setPermissionGrantState(adminComponent, packageName, permission, newState);
                                String status = isChecked ? "Granted" : "Denied";
                                Toast.makeText(AppDetailActivity.this, shortName + " " + status, Toast.LENGTH_SHORT).show();

                            } catch (SecurityException e) {
                                buttonView.setChecked(!isChecked);
                            }
                        }
                    });

                } else {
                    permSwitch.setChecked(true);
                    permSwitch.setEnabled(false);
                }
                permissionContainer.addView(permSwitch);
            }

        } catch (Exception e) {
            Logger.log(this, TAG, "Could not load permissions: " + e.getMessage());
        }
    }
}

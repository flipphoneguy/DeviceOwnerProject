package com.example.deviceownerapp;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Bundle;
import android.util.Log;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_detail_layout);

        // Get package name from the Intent
        packageName = getIntent().getStringExtra("packageName");
        if (packageName == null) {
            Toast.makeText(this, "Error: No package name.", Toast.LENGTH_SHORT).show();
            Logger.log(this, TAG, "AppDetailActivity started without a package name.");
            finish();
            return;
        }

        // Get managers and components
        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        pm = getPackageManager();
        adminComponent = new ComponentName(this, DeviceAdmin.class);

        // Find views
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
            String error = "Could not find package info for " + packageName;
            Logger.log(this, TAG, error + ": " + e.getMessage());
            appNameText.setText("Error: App not found");
        }
    }

    private void setupHideSwitch() {
        // Set the switch to the current state
        try {
            boolean isHidden = dpm.isApplicationHidden(adminComponent, packageName);
            hideSwitch.setChecked(isHidden);
        } catch (SecurityException e) {
            String error = "Not DO, cannot check hidden status for " + packageName;
            Logger.log(this, TAG, error + ": " + e.getMessage());
            hideSwitch.setEnabled(false);
        }

        // Set the listener to act when toggled
        hideSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                try {
                    // isChecked is the new state
                    dpm.setApplicationHidden(adminComponent, packageName, isChecked);
                    String status = isChecked ? "hidden" : "unhidden";
                    Toast.makeText(AppDetailActivity.this, "App " + status, Toast.LENGTH_SHORT).show();
                } catch (SecurityException e) {
                    String error = "Failed to change hidden state for " + packageName;
                    Logger.log(AppDetailActivity.this, TAG, error + ": " + e.getMessage());
                    Toast.makeText(AppDetailActivity.this, "Error: Not Device Owner", Toast.LENGTH_SHORT).show();
                    // Revert the switch if it failed
                    buttonView.setChecked(!isChecked);
                }
            }
        });
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

            // Loop through every permission and create a Switch for it
            for (final String permission : permissions) { // Added 'final' for the inner class

                // Check if it's a runtime permission (dangerous)
                boolean isRuntime = false;
                try {
                    PermissionInfo pInfo = pm.getPermissionInfo(permission, 0);
                    if (pInfo.protectionLevel == PermissionInfo.PROTECTION_DANGEROUS) {
                        isRuntime = true;
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    // Not a 'dangerous' permission, so we can't toggle it
                }

                Switch permSwitch = new Switch(this);
                // Get the short name (e.g., "CAMERA")
                final String shortName = permission.substring(permission.lastIndexOf(".") + 1); // Added 'final'
                permSwitch.setText(shortName);
                permSwitch.setMinHeight(120); // Make switch easier to tap

                if (isRuntime) {
                    // Check its current grant state
                    int grantState = dpm.getPermissionGrantState(adminComponent, packageName, permission);
                    permSwitch.setChecked(grantState == DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);

                    // Add listener to grant/deny on toggle (Java 1.7 compatible)
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
                                String error = "Failed to set permission " + permission + " for " + packageName;
                                Logger.log(AppDetailActivity.this, TAG, error + ": " + e.getMessage());
                                Toast.makeText(AppDetailActivity.this, "Error: Not DO", Toast.LENGTH_SHORT).show();
                                buttonView.setChecked(!isChecked);
                            }
                        }
                    });

                } else {
                    // Not a runtime permission, so just show its state as "on" (granted)
                    // and disable the switch.
                    permSwitch.setChecked(true);
                    permSwitch.setEnabled(false);
                }

                // Add the new switch to our container
                permissionContainer.addView(permSwitch);
            }

        } catch (Exception e) {
            String error = "Could not load permissions for " + packageName;
            Logger.log(this, TAG, error + ": " + e.getMessage());
        }
    }
}

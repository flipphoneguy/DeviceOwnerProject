package com.example.deviceownerapp;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

/**
 * This is the core component that receives administrator-level callbacks.
 * It's mostly here to be declared in the manifest so the system
 * recognizes our app as a potential admin.
 */
public class DeviceAdmin extends DeviceAdminReceiver {

    private static final String TAG = "DeviceAdmin";

    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Toast.makeText(context, "Device Admin Enabled", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Device Admin Enabled");
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Toast.makeText(context, "Device Admin Disabled", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Device Admin Disabled");
    }
}
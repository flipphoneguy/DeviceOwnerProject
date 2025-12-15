package com.example.deviceownerapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

/**
 * This receiver catches the result from the PackageInstaller
 * and shows a simple Toast message.
 */
public class InstallResultReceiver extends BroadcastReceiver {

    private static final String TAG = "InstallReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) {
            Logger.log(context, TAG, "Install result intent had no extras.");
            return;
        }

        int status = extras.getInt(PackageInstaller.EXTRA_STATUS);
        String message = extras.getString(PackageInstaller.EXTRA_STATUS_MESSAGE);

        switch (status) {
            case PackageInstaller.STATUS_SUCCESS:
                Toast.makeText(context, "Install Success!", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Install Success");
                break;
            default:
                String error = "Install Failed: " + status + ", " + message;
                Toast.makeText(context, error, Toast.LENGTH_LONG).show();
                // Log the failure message
                Logger.log(context, TAG, error);
                break;
        }
    }
}
package com.example.deviceownerapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Bundle;
import android.util.Log;

/**
 * This receiver catches the result from the PackageInstaller
 * and launches ResultActivity to show a Dialog.
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

        Intent resultIntent = new Intent(context, ResultActivity.class);
        resultIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        switch (status) {
            case PackageInstaller.STATUS_SUCCESS:
                Log.d(TAG, "Install Success");
                resultIntent.putExtra("title", "Success");
                resultIntent.putExtra("message", "App installed successfully!");
                resultIntent.putExtra("isError", false);
                break;
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                // If we need user action (shouldn't happen for Device Owner silent install),
                // we must launch the intent provided in EXTRA_INTENT.
                Intent confirmIntent = (Intent) extras.get(Intent.EXTRA_INTENT);
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirmIntent);
                }
                return; // Don't show result dialog yet
            default:
                String error = "Install Failed: " + status + (message != null ? ", " + message : "");
                Logger.log(context, TAG, error);
                resultIntent.putExtra("title", "Installation Failed");
                resultIntent.putExtra("message", error);
                resultIntent.putExtra("isError", true);
                break;
        }

        context.startActivity(resultIntent);
    }
}

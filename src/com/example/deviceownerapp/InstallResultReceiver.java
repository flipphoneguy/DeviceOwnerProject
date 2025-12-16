package com.example.deviceownerapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Bundle;

/**
 * This receiver catches the result from the PackageInstaller.
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

        if (status == PackageInstaller.STATUS_SUCCESS) {
            // Show success dialog via ProgressActivity
            Intent successIntent = new Intent(context, ProgressActivity.class);
            successIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            successIntent.putExtra(ProgressActivity.EXTRA_SUCCESS, "Application installed successfully.");
            context.startActivity(successIntent);
        } else if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirmIntent != null) {
                confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(confirmIntent);
            }
        } else {
            String error = "Install Failed: " + status + " (" + message + ")";
            Intent errorIntent = new Intent(context, ProgressActivity.class);
            errorIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            errorIntent.putExtra(ProgressActivity.EXTRA_ERROR, error);
            context.startActivity(errorIntent);
            Logger.log(context, TAG, error);
        }
    }
}

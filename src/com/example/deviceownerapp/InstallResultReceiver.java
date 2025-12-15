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
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                // This asks for user confirmation to install.
                // Since we are Device Owner, this shouldn't happen usually, but if it does, launch the intent.
                Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirmIntent);
                }
                break;
            default:
                String error = "Install Failed: " + status + " (" + message + ")";
                // Show as dialog if possible, but Receiver context is limited.
                // To show a Dialog, we need an Activity context.
                // We can start an activity to show the error.
                Intent errorIntent = new Intent(context, ProgressActivity.class); // Re-use ProgressActivity just to hold the dialog? Or new ErrorActivity?
                // Actually ErrorHandler uses AlertDialog which needs Activity.
                // Let's create a specialized intent to launch an activity that shows the error.
                // Or simply log it and toast it, but user asked for "Pop up where user has to click OK".

                // We can launch MainActivity with an error extra, or a transient transparent activity.
                // Let's use a new intent to ProgressActivity (or similar) that shows the error dialog then finishes.

                // Let's modify ProgressActivity to handle "SHOW_ERROR"
                Intent errIntent = new Intent(context, ProgressActivity.class);
                errIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                errIntent.putExtra("ERROR_MESSAGE", error);
                context.startActivity(errIntent);

                // Log the failure message
                Logger.log(context, TAG, error);
                break;
        }
    }
}

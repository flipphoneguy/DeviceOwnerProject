package com.example.deviceownerapp;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;

public class ErrorHandler {

    public static void showError(final Context context, final String message) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    new AlertDialog.Builder(context)
                        .setTitle("Error")
                        .setMessage(message + "\n\nLog file: " + Logger.getLogFilePath(context))
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .setCancelable(false)
                        .show();
                } catch (Exception e) {
                    // Fallback if context is not valid for dialog
                    e.printStackTrace();
                }
            }
        });
    }
}

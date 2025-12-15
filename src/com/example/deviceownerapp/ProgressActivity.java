package com.example.deviceownerapp;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.widget.TextView;
import android.app.AlertDialog;
import android.content.DialogInterface;

/**
 * Activity to show a progress spinner while installing.
 * It stays open until it receives a broadcast to close or finish.
 * Also handles showing errors and success messages from InstallResultReceiver.
 */
public class ProgressActivity extends Activity {

    public static final String ACTION_FINISH = "com.example.deviceownerapp.ACTION_FINISH_PROGRESS";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_ERROR = "ERROR_MESSAGE";
    public static final String EXTRA_SUCCESS = "SUCCESS_MESSAGE";

    private BroadcastReceiver finishReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Handle Success
        if (getIntent().hasExtra(EXTRA_SUCCESS)) {
            String msg = getIntent().getStringExtra(EXTRA_SUCCESS);
            new AlertDialog.Builder(this)
                .setTitle("Success")
                .setMessage(msg)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        finish();
                    }
                })
                .setCancelable(false)
                .show();
            // Close any existing "Processing" instances?
            // Sending broadcast here might kill *this* activity if we registered receiver,
            // but we register receiver after this block.
            sendBroadcast(new Intent(ACTION_FINISH));
            return;
        }

        // Handle Error
        if (getIntent().hasExtra(EXTRA_ERROR)) {
            String errorMsg = getIntent().getStringExtra(EXTRA_ERROR);
            new AlertDialog.Builder(this)
                .setTitle("Installation Error")
                .setMessage(errorMsg + "\n\nLog file: " + Logger.getLogFilePath(this))
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        finish();
                    }
                })
                .setCancelable(false)
                .show();
            sendBroadcast(new Intent(ACTION_FINISH));
            return;
        }

        setContentView(R.layout.activity_progress);

        TextView textView = findViewById(R.id.progress_text);
        if (getIntent().hasExtra(EXTRA_MESSAGE)) {
            textView.setText(getIntent().getStringExtra(EXTRA_MESSAGE));
        }

        registerReceiver(finishReceiver, new IntentFilter(ACTION_FINISH));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(finishReceiver);
        } catch (IllegalArgumentException e) {
        }
    }

    @Override
    public void onBackPressed() {
        if (!getIntent().hasExtra(EXTRA_ERROR) && !getIntent().hasExtra(EXTRA_SUCCESS)) {
             // Block back press during progress
        } else {
            super.onBackPressed();
        }
    }
}

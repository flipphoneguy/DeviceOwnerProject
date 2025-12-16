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
        setContentView(R.layout.activity_progress);

        registerReceiver(finishReceiver, new IntentFilter(ACTION_FINISH));

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        // Handle Success
        if (intent.hasExtra(EXTRA_SUCCESS)) {
            String msg = intent.getStringExtra(EXTRA_SUCCESS);
            showDialog("Success", msg);
            return;
        }

        // Handle Error
        if (intent.hasExtra(EXTRA_ERROR)) {
            String errorMsg = intent.getStringExtra(EXTRA_ERROR);
            showDialog("Installation Error", errorMsg + "\n\nLog file: " + Logger.getLogFilePath(this));
            return;
        }

        // Handle Message Update
        if (intent.hasExtra(EXTRA_MESSAGE)) {
            TextView textView = findViewById(R.id.progress_text);
            if (textView != null) {
                textView.setText(intent.getStringExtra(EXTRA_MESSAGE));
            }
        }
    }

    private void showDialog(String title, String message) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    finish();
                }
            })
            .setCancelable(false)
            .show();
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

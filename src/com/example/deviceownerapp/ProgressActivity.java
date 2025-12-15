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
 * Also handles showing errors from InstallResultReceiver.
 */
public class ProgressActivity extends Activity {

    public static final String ACTION_FINISH = "com.example.deviceownerapp.ACTION_FINISH_PROGRESS";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_ERROR = "ERROR_MESSAGE";

    private BroadcastReceiver finishReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getIntent().hasExtra(EXTRA_ERROR)) {
            // Show error dialog and finish when dismissed
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
            return; // Don't set content view
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
            // Not registered
        }
    }

    @Override
    public void onBackPressed() {
        // Prevent back press to ensure user waits if installing
        if (!getIntent().hasExtra(EXTRA_ERROR)) {
             // maybe warn?
        } else {
            super.onBackPressed();
        }
    }
}

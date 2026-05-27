package com.example.deviceownerapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

public class ProgressActivity extends Activity {

    public static final String ACTION_FINISH = "com.example.deviceownerapp.ACTION_FINISH_PROGRESS";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_ERROR = "ERROR_MESSAGE";
    public static final String EXTRA_SUCCESS = "SUCCESS_MESSAGE";

    private final BroadcastReceiver finishReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { finish(); }
    };

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(ThemeHelper.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_progress);

        IntentFilter filter = new IntentFilter(ACTION_FINISH);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(finishReceiver, filter, 4);
        } else {
            registerReceiver(finishReceiver, filter);
        }

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent.hasExtra(EXTRA_SUCCESS)) {
            showDialog(getString(R.string.dialog_success_title),
                    intent.getStringExtra(EXTRA_SUCCESS));
            return;
        }
        if (intent.hasExtra(EXTRA_ERROR)) {
            String msg = intent.getStringExtra(EXTRA_ERROR);
            showDialog(getString(R.string.dialog_error_title),
                    msg + "\n\nLog: " + Logger.getLogFilePath(this));
            return;
        }
        if (intent.hasExtra(EXTRA_MESSAGE)) {
            TextView tv = findViewById(R.id.progress_text);
            if (tv != null) tv.setText(intent.getStringExtra(EXTRA_MESSAGE));
        }
    }

    private void showDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        finish();
                    }
                })
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(finishReceiver); }
        catch (IllegalArgumentException ignored) {}
    }

    @Override
    public void onBackPressed() {
        Intent i = getIntent();
        if (i != null && (i.hasExtra(EXTRA_ERROR) || i.hasExtra(EXTRA_SUCCESS))) {
            super.onBackPressed();
        }
    }
}

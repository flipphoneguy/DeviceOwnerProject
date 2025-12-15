package com.example.deviceownerapp;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * A simple helper class to log errors to a file.
 * This logs to /storage/emulated/0/Android/data/com.example.deviceownerapp/files/app_errors.log
 * This location requires NO special storage permissions.
 */
public class Logger {

    private static final String LOG_FILE_NAME = "app_errors.log";
    private static final String TAG = "AppLogger";

    /**
     * Writes a log message to both logcat (e) and a file.
     * @param context Context to find the app's data directory
     * @param tag Log tag (e.g., "MainActivity")
     * @param message The error message to write
     */
    public static synchronized void log(Context context, String tag, String message) {
        // Also log to regular logcat
        Log.e(tag, message);

        try {
            // Get the app-specific external files directory
            File logDir = context.getExternalFilesDir(null);
            if (logDir == null) {
                Log.e(TAG, "Cannot get external files dir to log.");
                return;
            }

            File logFile = new File(logDir, LOG_FILE_NAME);

            // Get current timestamp
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            String logEntry = String.format("%s [%s]: %s\n", timestamp, tag, message);

            // Write to the file in "append" mode using try-with-resources
            try (FileOutputStream fos = new FileOutputStream(logFile, true);
                 OutputStreamWriter writer = new OutputStreamWriter(fos)) {
                writer.append(logEntry);
            }

        } catch (Exception e) {
            // If the logger itself fails, just print to logcat
            Log.e(TAG, "Critical: Failed to write to log file", e);
        }
    }

    public static String getLogFilePath(Context context) {
        File logDir = context.getExternalFilesDir(null);
        if (logDir != null) {
            return new File(logDir, LOG_FILE_NAME).getAbsolutePath();
        }
        return "Unknown";
    }
}
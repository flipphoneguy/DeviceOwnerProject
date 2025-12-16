package com.example.deviceownerapp;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * A simple helper class to log errors to a file.
 * This logs to /storage/emulated/0/Android/data/com.example.deviceownerapp/files/app_errors.log
 * This location requires NO special storage permissions.
 */
public class Logger {

    private static final String LOG_FILE_NAME = "app_errors.log";
    private static final String TAG = "AppLogger";
    private static final String SEPARATOR = "----------------------------------------";
    private static final int MAX_LOG_ENTRIES = 10;

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
            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            File logFile = new File(logDir, LOG_FILE_NAME);

            // Read existing logs
            List<String> logEntries = new ArrayList<>();
            if (logFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(logFile)))) {
                    StringBuilder currentEntry = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.equals(SEPARATOR)) {
                            if (currentEntry.length() > 0) {
                                logEntries.add(currentEntry.toString().trim());
                                currentEntry.setLength(0);
                            }
                        } else {
                            currentEntry.append(line).append("\n");
                        }
                    }
                    if (currentEntry.length() > 0) {
                        logEntries.add(currentEntry.toString().trim());
                    }
                }
            }

            // Create new entry
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            String newEntry = String.format("%s [%s]: %s", timestamp, tag, message);
            logEntries.add(newEntry);

            // Prune old entries
            while (logEntries.size() > MAX_LOG_ENTRIES) {
                logEntries.remove(0);
            }

            // Write back to file
            try (FileOutputStream fos = new FileOutputStream(logFile, false); // false to overwrite
                 OutputStreamWriter writer = new OutputStreamWriter(fos)) {
                for (String entry : logEntries) {
                    writer.write(entry);
                    writer.write("\n");
                    writer.write(SEPARATOR);
                    writer.write("\n\n");
                }
            }

        } catch (Exception e) {
            // If the logger itself fails, just print to logcat
            Log.e(TAG, "Critical: Failed to write to log file", e);
        }
    }

    public static String getLogFilePath(Context context) {
        try {
            File logDir = context.getExternalFilesDir(null);
            if (logDir != null) {
                return new File(logDir, LOG_FILE_NAME).getAbsolutePath();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Unknown";
    }
}

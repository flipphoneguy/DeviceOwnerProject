package com.example.deviceownerapp;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import android.content.DialogInterface;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_PICK_FILE = 1001;
    // Release API: https://api.github.com/repos/flipphoneguy/DeviceOwnerProject/releases/latest
    // Asset URL (fallback): https://github.com/flipphoneguy/DeviceOwnerProject/releases/latest/download/DeviceAdminApp.apk
    private static final String UPDATE_API_URL = "https://api.github.com/repos/flipphoneguy/DeviceOwnerProject/releases/latest";
    private static final String UPDATE_DOWNLOAD_URL = "https://github.com/flipphoneguy/DeviceOwnerProject/releases/latest/download/DeviceAdminApp.apk";

    private ListView appListView;
    private Button uninstallButton;
    private Button installFileButton;
    private Button optionsButton;
    private Button dhizukuButton;
    private TextView statusText;
    private DevicePolicyManager dpm;
    private PackageManager pm;
    private ComponentName adminComponent;
    private List<ApplicationInfo> appList;
    private AppAdapter appAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get core managers
        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        pm = getPackageManager();
        adminComponent = new ComponentName(this, DeviceAdmin.class);

        // Setup the list
        appList = new ArrayList<>();
        appListView = findViewById(R.id.app_list);
        uninstallButton = findViewById(R.id.uninstall_button);
        installFileButton = findViewById(R.id.install_file_button);
        optionsButton = findViewById(R.id.options_button);
        statusText = findViewById(R.id.status_text);
        dhizukuButton = findViewById(R.id.dhizuku_button);

        appAdapter = new AppAdapter();
        appListView.setAdapter(appAdapter);

        // Setup Dhizuku button
        setupDhizukuButton();

        // Set click listener for the app list
        appListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ApplicationInfo clickedApp = appList.get(position);
                Intent intent = new Intent(MainActivity.this, AppDetailActivity.class);
                intent.putExtra("packageName", clickedApp.packageName);
                startActivity(intent);
            }
        });

        // Set click listener for the Uninstall button
        uninstallButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeAdminAndUninstall();
            }
        });

        // Set click listener for the Install File button
        installFileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFilePicker();
            }
        });

        // Set click listener for the Options button
        if (optionsButton != null) {
            optionsButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openOptionsMenu();
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatusDisplay();
        loadApplications();
    }

    private void setupDhizukuButton() {
        if (dhizukuButton == null) return;

        dhizukuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!DpmHelper.isDhizukuInstalled(MainActivity.this)) {
                    Toast.makeText(MainActivity.this, R.string.dhizuku_not_available, Toast.LENGTH_SHORT).show();
                    return;
                }

                DpmHelper.requestDhizukuPermission(MainActivity.this, new DpmHelper.PermissionCallback() {
                    @Override
                    public void onResult(boolean granted) {
                        if (granted) {
                            Toast.makeText(MainActivity.this, R.string.dhizuku_permission_granted, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, R.string.dhizuku_permission_denied, Toast.LENGTH_SHORT).show();
                        }
                        updateStatusDisplay();
                        loadApplications();
                    }
                });
            }
        });
    }

    private void updateStatusDisplay() {
        if (statusText == null) return;

        DpmHelper.Mode mode = DpmHelper.getActiveMode(this);
        switch (mode) {
            case NATIVE_OWNER:
                statusText.setText(R.string.status_native_owner);
                statusText.setTextColor(0xFF4CAF50); // Green
                if (dhizukuButton != null) {
                    dhizukuButton.setVisibility(View.GONE);
                }
                break;
            case DHIZUKU:
                statusText.setText(R.string.status_dhizuku_connected);
                statusText.setTextColor(0xFF2196F3); // Blue
                if (dhizukuButton != null) {
                    dhizukuButton.setVisibility(View.GONE);
                }
                break;
            case NONE:
            default:
                statusText.setText(R.string.status_no_privileges);
                statusText.setTextColor(0xFFFF5722); // Orange
                // Show Dhizuku button if Dhizuku is installed but not connected
                if (dhizukuButton != null) {
                    if (DpmHelper.isDhizukuInstalled(this)) {
                        dhizukuButton.setVisibility(View.VISIBLE);
                    } else {
                        dhizukuButton.setVisibility(View.GONE);
                    }
                }
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_update) {
            checkForUpdates();
            return true;
        } else if (id == R.id.action_contact) {
            showContactDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void checkForUpdates() {
        Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show();
        new CheckUpdateTask().execute(UPDATE_API_URL);
    }

    private class CheckUpdateTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            try {
                URL url = new URL(params[0]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "DeviceOwnerApp"); // GitHub requires User-Agent
                connection.connect();

                if (connection.getResponseCode() != 200) {
                     return null;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                // Manual simple JSON parsing to find "tag_name"
                // JSON: { ... "tag_name": "v1.2", ... }
                String json = sb.toString();
                String key = "\"tag_name\"";
                int index = json.indexOf(key);
                if (index != -1) {
                    int startQuote = json.indexOf("\"", index + key.length()); // skip key
                    // The next quote should be start of value, but might be colon first
                    // Actually, indexOf after key length might hit the colon or whitespace
                    // Let's look for colon first
                    int colon = json.indexOf(":", index + key.length());
                    int valueStart = json.indexOf("\"", colon);
                    int valueEnd = json.indexOf("\"", valueStart + 1);
                    if (valueStart != -1 && valueEnd != -1) {
                        return json.substring(valueStart + 1, valueEnd);
                    }
                }
                return null;
            } catch (Exception e) {
                Logger.log(MainActivity.this, TAG, "Update check failed: " + e.getMessage());
                return null;
            }
        }

        @Override
        protected void onPostExecute(String remoteVersion) {
            if (remoteVersion != null) {
                try {
                    PackageInfo pInfo = pm.getPackageInfo(getPackageName(), 0);
                    String currentVersion = pInfo.versionName;

                    // Simple string comparison for versions (works if format is consistent like v1.0, v1.1)
                    // If remote is different from current, assume update (or we could try to parse numbers)
                    // Removing 'v' prefix if exists
                    String cleanRemote = remoteVersion.replace("v", "");
                    String cleanCurrent = currentVersion.replace("v", "");

                    if (!cleanRemote.equals(cleanCurrent)) {
                         // Found update
                         showUpdateDialog(remoteVersion);
                    } else {
                        Toast.makeText(MainActivity.this, "You are up to date (Version " + currentVersion + ")", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Error checking version.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(MainActivity.this, "Failed to check for updates.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showUpdateDialog(String newVersion) {
        new AlertDialog.Builder(this)
            .setTitle("Update Available")
            .setMessage("A new version (" + newVersion + ") is available. Download and install now?")
            .setPositiveButton("Download", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    new DownloadUpdateTask().execute(UPDATE_DOWNLOAD_URL);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private class DownloadUpdateTask extends AsyncTask<String, Void, File> {
        @Override
        protected File doInBackground(String... params) {
            try {
                URL url = new URL(params[0]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                // Follow redirects (GitHub downloads often redirect)
                connection.setInstanceFollowRedirects(true);
                connection.connect();

                // Handle redirect manually if needed, but HttpURLConnection usually handles it
                // unless switching protocols (http->https) which shouldn't happen here.

                File file = new File(getExternalCacheDir(), "update.apk");
                FileOutputStream fileOutput = new FileOutputStream(file);
                InputStream inputStream = connection.getInputStream();

                byte[] buffer = new byte[1024];
                int bufferLength;

                while ((bufferLength = inputStream.read(buffer)) > 0) {
                    fileOutput.write(buffer, 0, bufferLength);
                }
                fileOutput.close();
                return file;
            } catch (Exception e) {
                Logger.log(MainActivity.this, TAG, "Update download failed: " + e.getMessage());
                return null;
            }
        }

        @Override
        protected void onPostExecute(File result) {
            if (result != null) {
                Toast.makeText(MainActivity.this, "Update downloaded. Installing...", Toast.LENGTH_LONG).show();
                installUpdate(result);
            } else {
                Toast.makeText(MainActivity.this, "Update download failed. Check log.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void installUpdate(File file) {
        Intent intent = new Intent(this, InstallActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        // Use SimpleFileProvider to avoid FileUriExposedException on Android 7+
        Uri uri = Uri.parse("content://" + getPackageName() + ".fileprovider/" + file.getName());
        intent.setData(uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    private void showContactDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Contact Us");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        final EditText nameInput = new EditText(this);
        nameInput.setHint("Name");
        layout.addView(nameInput);

        final EditText emailInput = new EditText(this);
        emailInput.setHint("Email");
        layout.addView(emailInput);

        final EditText messageInput = new EditText(this);
        messageInput.setHint("Message");
        messageInput.setMinLines(3);
        layout.addView(messageInput);

        builder.setView(layout);

        builder.setPositiveButton("Send", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String name = nameInput.getText().toString();
                String email = emailInput.getText().toString();
                String message = messageInput.getText().toString();

                if (name.isEmpty() || email.isEmpty() || message.isEmpty()) {
                    Toast.makeText(MainActivity.this, "All fields are required.", Toast.LENGTH_SHORT).show();
                    return;
                }

                new SendFeedbackTask().execute(name, email, message);
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private class SendFeedbackTask extends AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... params) {
            String name = params[0];
            String email = params[1];
            String message = params[2];
            String jsonPayload = String.format("{\"name\": \"%s\", \"email\": \"%s\", \"message\": \"%s\"}",
                    escapeJson(name), escapeJson(email), escapeJson(message));

            try {
                URL url = new URL("https://formspree.io/mnnwyprr");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);

                try (java.io.OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                return code >= 200 && code < 300;

            } catch (Exception e) {
                Logger.log(MainActivity.this, TAG, "Feedback send failed: " + e.getMessage());
                return false;
            }
        }

        private String escapeJson(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\b", "\\b")
                    .replace("\f", "\\f")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Success")
                    .setMessage("Thanks for your feedback!")
                    .setPositiveButton("OK", null)
                    .show();
            } else {
                Toast.makeText(MainActivity.this, "Failed to send feedback. Check log.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*"); // Allow user to pick any file, we validate later
        // Optionally suggest mime types if API 19+
        String[] mimeTypes = {"application/vnd.android.package-archive", "application/zip", "application/octet-stream"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

        try {
            startActivityForResult(Intent.createChooser(intent, "Select File"), REQUEST_PICK_FILE);
        } catch (Exception e) {
            ErrorHandler.showError(this, "Could not open file picker: " + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PICK_FILE && resultCode == Activity.RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();
                // Launch InstallActivity to handle the installation
                Intent installIntent = new Intent(this, InstallActivity.class);
                installIntent.setAction(Intent.ACTION_VIEW);
                installIntent.setData(uri);
                // Grant read permission to the target activity
                installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(installIntent);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void removeAdminAndUninstall() {
        try {
            DpmHelper.Mode mode = DpmHelper.getActiveMode(this);

            // 1. Check if we are Device Owner and clear it (only in native mode)
            if (mode == DpmHelper.Mode.NATIVE_OWNER) {
                Toast.makeText(this, "Clearing Device Owner status...", Toast.LENGTH_SHORT).show();
                DpmHelper.clearDeviceOwner(this);
            }

            // 2. Remove Active Admin status
            Toast.makeText(this, "Removing Active Admin...", Toast.LENGTH_SHORT).show();
            DpmHelper.removeActiveAdmin(this);

            // 3. Launch the system uninstall dialog
            Uri packageUri = Uri.parse("package:" + getPackageName());
            Intent uninstallIntent = new Intent(Intent.ACTION_DELETE, packageUri);
            startActivity(uninstallIntent);

        } catch (Exception e) {
            Logger.log(this, TAG, "Error removing admin: " + e.getMessage());
            ErrorHandler.showError(this, "Error removing admin: " + e.getMessage());
        }
    }

    private void loadApplications() {
        DpmHelper.Mode mode = DpmHelper.getActiveMode(this);
        if (mode == DpmHelper.Mode.NONE) {
            // Update the button text if we aren't admin anymore
            if (uninstallButton != null) {
                 uninstallButton.setText("Uninstall App (Not Admin)");
            }
        } else {
            if (uninstallButton != null) {
                 uninstallButton.setText("Uninstall App / Remove Admin");
            }
        }

        // Get all apps
        appList = pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES);

        Collections.sort(appList, new Comparator<ApplicationInfo>() {
            @Override
            public int compare(ApplicationInfo a1, ApplicationInfo a2) {
                try {
                    return a1.loadLabel(pm).toString().compareToIgnoreCase(a2.loadLabel(pm).toString());
                } catch (Exception e) {
                    return 0;
                }
            }
        });

        appAdapter.notifyDataSetChanged();
    }

    private class AppAdapter extends BaseAdapter {
        @Override
        public int getCount() { return appList.size(); }
        @Override
        public Object getItem(int position) { return appList.get(position); }
        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(android.R.layout.simple_list_item_1, parent, false);
                holder = new ViewHolder();
                holder.textView = (TextView) convertView;
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            ApplicationInfo app = appList.get(position);
            String appName = app.loadLabel(pm).toString();
            boolean isHidden = DpmHelper.isApplicationHidden(MainActivity.this, app.packageName);

            String displayName = appName + (isHidden ? " (Hidden)" : "");
            holder.textView.setText(displayName);
            
            return convertView;
        }

        private class ViewHolder {
            TextView textView;
        }
    }
}

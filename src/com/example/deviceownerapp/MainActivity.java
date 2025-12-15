package com.example.deviceownerapp;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_PICK_FILE = 1001;

    private ListView appListView;
    private Button uninstallButton;
    private Button installFileButton;
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
        
        appAdapter = new AppAdapter();
        appListView.setAdapter(appAdapter);

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
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadApplications();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*"); // Allow user to pick any file, we validate later
        // Optionally suggest mime types if API 19+
        String[] mimeTypes = {"application/vnd.android.package-archive", "application/zip", "application/octet-stream"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

        try {
            startActivityForResult(intent, REQUEST_PICK_FILE);
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

    private void removeAdminAndUninstall() {
        try {
            // 1. Check if we are Device Owner and clear it
            if (dpm.isDeviceOwnerApp(getPackageName())) {
                Toast.makeText(this, "Clearing Device Owner status...", Toast.LENGTH_SHORT).show();
                // This method removes the DO status, making us a normal app again
                @SuppressWarnings("deprecation")
                boolean ignored = false; // dummy
                dpm.clearDeviceOwnerApp(getPackageName());
            }

            // 2. Check if we are an Active Admin (Profile Owner or just Admin) and remove it
            if (dpm.isAdminActive(adminComponent)) {
                Toast.makeText(this, "Removing Active Admin...", Toast.LENGTH_SHORT).show();
                dpm.removeActiveAdmin(adminComponent);
            }

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
        if (!dpm.isDeviceOwnerApp(getPackageName())) {
            // Update the button text if we aren't admin anymore
            if (uninstallButton != null) {
                 uninstallButton.setText("Uninstall App (Not Admin)");
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
            boolean isHidden = false;

            try {
                isHidden = dpm.isApplicationHidden(adminComponent, app.packageName);
            } catch (SecurityException e) {
                // Not DO
            }

            String displayName = appName + (isHidden ? " (Hidden)" : "");
            holder.textView.setText(displayName);
            
            return convertView;
        }

        private class ViewHolder {
            TextView textView;
        }
    }
}

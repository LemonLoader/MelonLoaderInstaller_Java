package com.melonloader.installer.activites;

import android.Manifest;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.melonloader.installer.helpers.ApplicationFinder;
import com.melonloader.installer.R;
import com.melonloader.installer.UnityApplicationData;
import com.melonloader.installer.helpers.PackageWarningHelper;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {
    List<UnityApplicationData> unityApplications;
    ListView listview;
    Toast unsupportedToast;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AsyncTask.execute(() -> {
            boolean connectionAvailable = PackageWarningHelper.Run();
            if (!connectionAvailable) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder
                        .setTitle("Error")
                        .setMessage("Unable to connect to GitHub! Please check your connection and try again")
                        .setPositiveButton("Exit", (di, i) -> finishAndRemoveTask())
                        .setIcon(android.R.drawable.ic_dialog_alert);

                AlertDialog alert = builder.create();
                alert.setCancelable(false);
                alert.show();
            }
        });

        unityApplications = ApplicationFinder.GetSupportedApplications(this);

        SupportedApplicationsAdapter adapter = new SupportedApplicationsAdapter(this, unityApplications);

        listview = (ListView) findViewById(R.id.application_list);
        listview.setAdapter(adapter);
        listview.setOnItemClickListener(this);

        requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},100);
        requestInstallUnknownSources();
    }

    public void requestInstallUnknownSources() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder
                .setTitle("Install Permission")
                .setMessage("Lemon needs permission to install apps from unknown sources to function!")
                .setPositiveButton("Setup", (Installation, p) -> {
                    try {
                        startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:com.melonloader.installer")));
                    } catch (Exception ex) {
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert);

        AlertDialog alert = builder.create();
        alert.setCancelable(false);
        alert.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public void onItemClick(AdapterView<?> l, View v, int position, long id) {
        Log.i("melonloader", "You clicked Item: " + id + " at position:" + position);
        UnityApplicationData app = unityApplications.get(position);

        if (!app.supported) {
            if (unsupportedToast == null)
                unsupportedToast = Toast.makeText(getApplicationContext(), "Unsupported application", Toast.LENGTH_SHORT);
            unsupportedToast.show();
            return;
        }

        // Then you start a new Activity via Intent
        Intent intent = new Intent();
        intent.setClass(this, ViewApplication.class);
        intent.putExtra("target.packageName", app.packageName);
        startActivity(intent);
    }

    public class SupportedApplicationsAdapter extends ArrayAdapter<UnityApplicationData> {
        public SupportedApplicationsAdapter(Context context, List<UnityApplicationData> apps) {
            super(context, 0, apps);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // Get the data item for this position
            UnityApplicationData application = getItem(position);
            // Check if an existing view is being reused, otherwise inflate the view
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_supported_application, parent, false);
            }
            // Lookup view for data population
            TextView applicationName = convertView.findViewById(R.id.applicationNameList);
            TextView unityVersion = convertView.findViewById(R.id.unityVersionList);
            ImageView applicationIcon = convertView.findViewById(R.id.applicationIconList);
            TextView applicationPatched = convertView.findViewById(R.id.isPatchedList);
            // Populate the data into the template view using the data object

            applicationName.setText(application.appName);
            applicationIcon.setImageDrawable(application.icon);

            if (!application.supported)
            {
                applicationPatched.setVisibility(View.VISIBLE);
                applicationPatched.setText("unsupported");
                applicationPatched.setTextColor(Color.RED);
            }
            else {
                applicationPatched.setVisibility(View.GONE);
                applicationPatched.setText("patched");
                applicationPatched.setTextColor(Color.GREEN);
            }

            applicationPatched.setVisibility(application.patched || !application.supported ? View.VISIBLE : View.GONE);

            if (application.unityVersion == null) {
                unityVersion.setVisibility(View.GONE);
                Path tempPath = Paths.get(getExternalFilesDir(null).toString(), "temp", application.appName);
                application.TryDetectVersion(tempPath.toString(), () -> { runOnUiThread(() -> { notifyDataSetChanged(); }); });
//                application.TryDetectVersion(tempPath.toString(), () -> { notifyDataSetChanged(); });
            } else {
                unityVersion.setText(application.unityVersion);
                unityVersion.setVisibility(View.VISIBLE);
            }

            // Return the completed view to render on screen
            return convertView;
        }
    }
}
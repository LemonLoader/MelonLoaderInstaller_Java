package com.melonloader.installer.helpers;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.melonloader.installer.BuildConfig;
import com.melonloader.installer.Callable;
import com.melonloader.installer.adbbridge.ADBBridgeHelper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ApkInstallerHelper {
    Activity context;
    String packageName;
    String lastInstallPath;
    public Boolean uninstallCanceled = false;

    Integer installLoopCount;

    String pending = null;
    Runnable next = null;
    Callable afterInstall = null;

    public ApkInstallerHelper(Activity _context, String _packageName)
    {
        context = _context;
        packageName = _packageName;
        installLoopCount = 0;
    }

    public void InstallApk(String path, Callable doAfterInstall)
    {
        afterInstall = doAfterInstall;
        next = () -> InternalInstall(path);
        UninstallPackage(null);
    }

    protected void InternalInstall(String path)
    {
        lastInstallPath = path;
        AsyncTask.execute(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }

            context.runOnUiThread(() -> {
                Uri filePath = uriFromFile(context, new File(path));

                Intent install = new Intent(Intent.ACTION_VIEW);
                install.setDataAndType(filePath, "application/vnd.android.package-archive");

                install.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                install.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                try {
                    context.startActivityForResult(install, 2000);
                } catch (ActivityNotFoundException e) {
                    e.printStackTrace();
                    Log.e("TAG", "Error in opening the file!");
                }
            });
        });
    }

    private boolean isPackageInstalled(String packageName, PackageManager packageManager) {
        try {
            packageManager.getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public void UninstallPackage(Runnable nxt)
    {
        if (nxt != null)
            next = nxt;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder
                .setTitle("ADB Bridge")
                .setMessage("Do you want to use the Lemon ADB Bridge® to save game data and OBBs, if they exist?")
                .setPositiveButton("Yes", (dialogInterface, i) -> HandleBridge())
                .setNegativeButton("No", (dialogInterface, i) -> HandleStandard())
                .setIcon(android.R.drawable.ic_dialog_info);

        AlertDialog alert = builder.create();
        alert.setCancelable(false);
        alert.show();
    }

    protected void HandleBridge() {
        Log.i("MelonLoader", "Using ADBBridge");

        ADBBridgeHelper.AttemptConnect(context.getExternalFilesDir(null).toString(), packageName, new Callable() {
            @Override
            public void call() {
                onActivityResult(1000, 0, null);
            }

            @Override
            public void callOnFail() {}
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder
                .setTitle("ADB Bridge")
                .setMessage("Waiting...\nIf you haven't already, please confirm your device on the ADB Bridge client.")
                .setPositiveButton("Use Standard Uninstall", (dialogInterface, i) -> {
                    ADBBridgeHelper.Kill();
                    HandleStandard();
                })
                .setNegativeButton("Cancel", (dialogInterface, i) -> {
                    ADBBridgeHelper.Kill();
                    uninstallCanceled = true;
                    onActivityResult(1000, 0, null);
                })
                .setIcon(android.R.drawable.ic_dialog_info);

        AlertDialog alert = builder.create();
        alert.setCancelable(false);
        alert.show();
        ADBBridgeHelper.SetDialog(alert);
    }

    private boolean shouldMoveBack;
    private Path dataPath;
    private Path obbPath;
    private Path newDataPath;
    private Path newObbPath;

    protected void HandleStandard() {
        Log.i("MelonLoader", "Not using ADBBridge");
        context.runOnUiThread(() -> {
            dataPath = Paths.get("/sdcard/Android/data/" + packageName + "/");
            obbPath = Paths.get("/sdcard/Android/obb/" + packageName + "/");
            newDataPath = Paths.get("/sdcard/Android/data/" + packageName + "_LEMON/");
            newObbPath = Paths.get("/sdcard/Android/obb/" + packageName + "_LEMON/");
            shouldMoveBack = false;
            try {
                Files.move(dataPath, newDataPath);
                Files.move(obbPath, newObbPath);
                shouldMoveBack = true;

                pending = Intent.ACTION_DELETE;

                Intent intent = new Intent(Intent.ACTION_DELETE);
                intent.setData(Uri.parse("package:" + packageName));
                context.startActivityForResult(intent, 1000);
            } catch (IOException ignored) {
                AlertDialog.Builder builder = new AlertDialog.Builder(context)
                        .setTitle("Failed to save data!")
                        .setMessage("Failed to save any data stored in Android/data or Android/obb. This could break some games. Do you want to continue?")
                        .setPositiveButton("Yes", (dialogInterface, i) -> {
                            pending = Intent.ACTION_DELETE;

                            Intent intent = new Intent(Intent.ACTION_DELETE);
                            intent.setData(Uri.parse("package:" + packageName));
                            context.startActivityForResult(intent, 1000);
                        })
                        .setNegativeButton("No", (dialogInterface, i) -> {
                            uninstallCanceled = true;
                            onActivityResult(1000, 0, null);
                        });

                AlertDialog alert = builder.create();
                alert.setCancelable(false);
                alert.show();
            }
        });
    }

    private static Uri uriFromFile(Context context, File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", file);
        } else {
            return Uri.fromFile(file);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
    {
        Log.i("melonloader", "" + requestCode + " " + resultCode);

        if (requestCode == 1000)
        {
            if (shouldMoveBack) {
                try {
                    Files.move(newDataPath, dataPath);
                    Files.move(newObbPath, obbPath);
                } catch (IOException ignored) {
                    Toast.makeText(context, "Failed to restore data, check for renamed folders in Android directories!", Toast.LENGTH_LONG).show();
                }
            }

            if (uninstallCanceled && afterInstall != null) {
                pending = null;
                next = null;
                afterInstall.callOnFail();
                return;
            }

            pending = null;
            if (next != null)
                next.run();
            next = null;
        }
        if (requestCode == 2000)
        {
            if (!isPackageInstalled(packageName, context.getPackageManager())) {
                if (installLoopCount >= 3) {
                    afterInstall.callOnFail();
                    return;
                }

                Log.i("melonloader", "Package not installed, attempting installation");
                installLoopCount++;
                InternalInstall(lastInstallPath);
            }
            else {
                ADBBridgeHelper.Kill();
                if (afterInstall != null)
                    afterInstall.call();
                afterInstall = null;
            }
        }
    }
}

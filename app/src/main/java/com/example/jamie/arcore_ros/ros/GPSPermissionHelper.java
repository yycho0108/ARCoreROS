package com.example.jamie.arcore_ros.ros;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

public class GPSPermissionHelper {
    private static final int GPS_PERMISSION_CODE = 1;
    private static final String[] GPS_PERMISSIONS = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION};

    /**
     * Check to see we have the necessary permissions for this app, and ask for them if we don't.
     */
    public static void requestPermissions(Activity activity) {
        ActivityCompat.requestPermissions(activity, GPS_PERMISSIONS, GPS_PERMISSION_CODE);
    }

    public static boolean hasPermissions(Activity activity) {
        for (String s : GPS_PERMISSIONS) {
            boolean success = (ContextCompat.checkSelfPermission(activity, s)
                    == PackageManager.PERMISSION_GRANTED);
            if (!success) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check to see if we need to show the rationale for this permission.
     */
    public static boolean shouldShowRequestPermissionRationale(Activity activity) {
        for(String s : GPS_PERMISSIONS){
            boolean required = ActivityCompat.shouldShowRequestPermissionRationale(activity, s);
            if(required) return true;
        }
        return false;
    }

    /**
     * Launch Application Setting to grant permission.
     */
    public static void launchPermissionSettings(Activity activity) {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
        activity.startActivity(intent);
    }
}

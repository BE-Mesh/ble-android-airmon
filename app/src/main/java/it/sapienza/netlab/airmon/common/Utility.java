package it.sapienza.netlab.airmon.common;


import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;


public class Utility {

    private static String TAG = Utility.class.getSimpleName();

    //Use this check to determine whether BLE is supported on the device.
    //Return true if BLE is supported.
    public static boolean isBLESupported(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    //Return a List of ScanFilter objects to filter by Service UUID.
    public static List<ScanFilter> buildScanFilters() {
        List<ScanFilter> scanFilters = new ArrayList<>();

        ScanFilter.Builder builder = new ScanFilter.Builder();
        // Comment out the below lines to see all BLE devices around you
        builder.setServiceUuid(Constants.LocationServiceParcelUUID);
        scanFilters.add(builder.build());
        builder.setServiceUuid(Constants.TimeServiceParcelUUID);
        scanFilters.add(builder.build());
        Log.d(TAG, "buildScanFilters: "+scanFilters);
        return scanFilters;
    }

    //Return a ScanSettings object set to use low power (to preserve battery life).
    public static ScanSettings buildScanSettings() {
        ScanSettings.Builder builder = new ScanSettings.Builder();
        builder.setScanMode(ScanSettings.SCAN_MODE_LOW_POWER);
        return builder.build();
    }

}
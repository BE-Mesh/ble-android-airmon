package it.sapienza.netlab.airmon.listeners;

import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;


/**
 * Custom ScanCallback object - Every result is an user on the mesh network
 */
public class CustomScanCallback extends ScanCallback {

    private final static String TAG = CustomScanCallback.class.getName();

    private List<ScanResult> results;

    public CustomScanCallback() {
        results = new ArrayList<>();
    }

    @Override
    public void onBatchScanResults(List<ScanResult> results) {
        super.onBatchScanResults(results);
        results.addAll(results);
    }

    public List<ScanResult> getResults() {
        return results;
    }

    @Override
    public void onScanFailed(int errorCode) {
        super.onScanFailed(errorCode);
        switch (errorCode) {
            case SCAN_FAILED_ALREADY_STARTED:
                Log.e(TAG, "Scan already started");
                break;

            case SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                Log.e(TAG, "Scan failed application registration failed");
                break;

            case SCAN_FAILED_FEATURE_UNSUPPORTED:
                Log.e(TAG, "Scan failed,this feature is unsupported");
                break;

            case SCAN_FAILED_INTERNAL_ERROR:
                Log.e(TAG, "Scan failed internal error");
                break;

            default:
                Log.e(TAG, "Scan failed unidentified errorCode " + errorCode);
        }
    }


}

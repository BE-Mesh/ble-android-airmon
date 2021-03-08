package it.sapienza.netlab.airmon.listeners;

import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;


//Custom ScanCallback object
public class ServerScanCallback extends ScanCallback {

    private final static String TAG = ServerScanCallback.class.getName();
    private OnServerFoundMessageListener listener;
    private List<ScanResult> results;

    public ServerScanCallback(OnServerFoundMessageListener listener) {
        this.listener = listener;
        results = new ArrayList<>();
    }

    @Override
    public void onBatchScanResults(List<ScanResult> results) {
        super.onBatchScanResults(results);
    }

    @Override
    public void onScanResult(int callbackType, ScanResult result) {
        super.onScanResult(callbackType, result);

        for (ScanResult scanResult : this.getResults()) {
            if (scanResult.getDevice().getAddress().equals(result.getDevice().getAddress())) {
                Log.d(TAG, "Scan discarded " + result);
                return;
            }
        }

        this.results.add(result);
        listener.OnServerFound("New server found");
        Log.d(TAG, "onScanResult: " + result);
    }

    public List<ScanResult> getResults() {
        return results;
    }

    @Override
    public void onScanFailed(int errorCode) {
        super.onScanFailed(errorCode);
        switch (errorCode) {
            case SCAN_FAILED_ALREADY_STARTED:
                listener.OnErrorScan("Scan already started", errorCode);
                break;
            case SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                listener.OnErrorScan("Scan failed application registration failed", errorCode);
                break;
            case SCAN_FAILED_FEATURE_UNSUPPORTED:
                listener.OnErrorScan("Scan failed,this feature is unsupported", errorCode);
                break;
            case SCAN_FAILED_INTERNAL_ERROR:
                listener.OnErrorScan("Scan failed internal error", errorCode);
                break;
            default:
                listener.OnErrorScan("Scan failed unidentified errorCode " + errorCode, errorCode);
        }
    }

    public void clearResults() {
        results.clear();
    }

    public interface OnServerFoundMessageListener {
        void OnServerFound(String message);

        void OnErrorScan(String message, int errorCodeCallback);
    }
}
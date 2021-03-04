package it.sapienza.netlab.airmon.client;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.LinkedList;

import it.sapienza.netlab.airmon.common.Utility;
import it.sapienza.netlab.airmon.listeners.Listeners;
import it.sapienza.netlab.airmon.listeners.ServerScanCallback;
import it.sapienza.netlab.airmon.models.Server;
import it.sapienza.netlab.airmon.models.ServerList;
import it.sapienza.netlab.airmon.tasks.ConnectBLETask;

import static it.sapienza.netlab.airmon.common.ByteUtility.printByte;
import static it.sapienza.netlab.airmon.common.Utility.SCAN_PERIOD;

/**
 * This class implements the functionality needed by a BLE client in our BLE network. It masks the complexity of the job done in the lower tier, the ConnectBLETask
 */
public class BLEClient {

    private static final String TAG = BLEClient.class.getSimpleName();

    private static final long HANDLER_PERIOD = 5000;
    private static BLEClient singleton;
    private Context context;
    private ConnectBLETask connectBLETask;
    private BluetoothLeScanner mBluetoothLeScanner;
    private ServerScanCallback mScanCallback;
    private boolean isScanning = false;
    private BluetoothDevice serverDevice;
    private boolean isServiceStarted = false;
    private LinkedList<OnClientOnlineListener> listeners;
    private byte[] lastServerIdFound = new byte[2];
    private Listeners.OnConnectionLost OnConnectionListener;

    private BLEClient(Context context) {
        this.context = context;
        BluetoothManager mBluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        assert mBluetoothManager != null;
        BluetoothAdapter mBluetoothAdapter = mBluetoothManager.getAdapter();
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        listeners = new LinkedList<>();
    }

    public static synchronized BLEClient getInstance(Context context) {
        if (singleton == null)
            singleton = new BLEClient(context);
        return singleton;
    }

    public ConnectBLETask getConnectBLETask() {
        return connectBLETask;
    }

    public void addOnClientOnlineListener(OnClientOnlineListener list) {
        this.listeners.add(list);
    }
    private void startScanning() {
        isScanning = true;
        if (mScanCallback == null) {
            ServerList.cleanUserList();
            // Will stop the scanning after a set time.
            new Handler().postDelayed(this::initializeClient, SCAN_PERIOD);

            // Kick off a new scan.
            mScanCallback = new ServerScanCallback(new ServerScanCallback.OnServerFoundListener() {
                @Override
                public void OnServerFound(String message) {
                    Log.d(TAG, "OUD: OnServerFound: " + message);
                }

                @Override
                public void OnErrorScan(String message, int errorCodeCallback) {
                    Log.e(TAG, "OUD: OnServerFound: " + message);
                }
            });

            mBluetoothLeScanner.startScan(Utility.buildScanFilters(), Utility.buildScanSettings(), mScanCallback);

        } else {
            Log.d(TAG, "OUD: startScanning: Scanning already started ");
        }
    }

    private void initializeClient() {
        Log.d(TAG, "OUD: Stopping Scanning");
        // Stop the scan, wipe the callback.
        mBluetoothLeScanner.stopScan(mScanCallback);
        mScanCallback = null;
        isScanning = false;
        //Questa serve perché potrebbe esserci un ritardo per queste operazioni
        new Handler(Looper.getMainLooper()).postDelayed(() -> tryConnection(), 1000);
    }

    private void tryConnection() {
        if (isServiceStarted) {
            final int size = ServerList.getServerList().size();
                final Server newServer = ServerList.getServer(0);
                Log.d(TAG, "OUD: " + "tryConnection with: " + newServer.getUserName());
                final ConnectBLETask connectBLE = new ConnectBLETask(newServer, context);
                //connectBLE.addReceivedListener((idMitt, message, hop, sendTimeStamp) -> Log.d(TAG, "OnMessageReceived: Messaggio ricevuto dall'utente " + idMitt + ": " + message));
                connectBLE.setOnConnectionLostListener(() -> {
                    OnConnectionListener.OnConnectionLost();
                });
                

                connectBLE.startClient();
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (connectBLE.IsDeviceConnected()){
                        connectBLETask = connectBLE;
                        for (OnClientOnlineListener l : listeners) {
                            l.onClientOnline();
                        }
                        serverDevice = newServer.getBluetoothDevice();
                        Log.d(TAG, "OUD: You're a client ");
                    } else {
                        connectBLE.setJobDone();
                        Log.d(TAG, "OUD: " + "id non assegnato, passo al prossimo server");
                        //tryConnection(offset + 1);
                    }
                }, HANDLER_PERIOD);
            }
    }

    public void startClient() {
        isServiceStarted = true;
        Log.d(TAG, "OUD: startClient: Scan the background, search servers to join");
        startScanning();
    }


    public void stopClient() {
        if (connectBLETask != null) {
            connectBLETask.stopClient();
            connectBLETask = null;
        }
        isServiceStarted = false;

        Log.d(TAG, "OUD: stopClient: Service stopped");
        if (isScanning) {
            Log.d(TAG, "OUD: stopClient: Stopping Scanning");
            // Stop the scan, wipe the callback.
            mBluetoothLeScanner.stopScan(mScanCallback);
            mScanCallback = null;
            isScanning = false;
        }

    }


    public void sendMessage(String latitude, String longitude, String timestamp) {
        if (connectBLETask != null)
            connectBLETask.sendMessage(latitude, longitude, timestamp);
    }

    public boolean IsDeviceConnected() {
        if (connectBLETask != null) return connectBLETask.IsDeviceConnected();
        else return false;
    }

    public interface OnClientOnlineListener {
        void onClientOnline();
    }

    public void setOnConnectionLostListener(Listeners.OnConnectionLost l) {
        OnConnectionListener = l;
    }
}
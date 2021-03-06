package it.sapienza.netlab.airmon.tasks;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Calendar;
import it.sapienza.netlab.airmon.common.Constants;
import it.sapienza.netlab.airmon.common.RoutingTable;
import it.sapienza.netlab.airmon.common.Utility;
import it.sapienza.netlab.airmon.listeners.Listeners;
import it.sapienza.netlab.airmon.models.Server;
import it.sapienza.netlab.airmon.MainActivity;

import static androidx.core.content.ContextCompat.getSystemService;
import static it.sapienza.netlab.airmon.common.ByteUtility.getBit;
import static it.sapienza.netlab.airmon.common.ByteUtility.printByte;


/**
 * lower tier del BLEClient, implementa le varie primitive di invio e ricezione messaggi, l'inizializzazione del client e la gestione degli errori di base
 */
public class ConnectBLETask {
    private final static String TAG = ConnectBLETask.class.getName();
    private Server server;
    private BluetoothGattCallback mGattCallback;
    private BluetoothGatt mGatt;
    private Context context;
    private String id;
    private String serverId;
    private HashMap<String, String> messageMap;
    private ArrayList<Listeners.OnMessageReceivedListener> receivedListeners;
    private ArrayList<Listeners.OnMessageWithInternetListener> internetListeners;
    private RoutingTable routingTable;
    private Listeners.OnPacketSentListener onPacketSent;
    private Listeners.OnDisconnectedServerListener onDisconnectedServerListener;
    private byte[] lastServerIdFound = new byte[2];
    private boolean temporaryClient;
    private Listeners.OnJobDoneListener onJobDoneListener;
    private boolean jobDone = false;
    private Listeners.OnConnectionLost OnConnectionLostListener;
    private int maxAttempt;
    private byte[] latitude = null, longitude = null, timestamp = null;
    private boolean isConnected = false;

    public ConnectBLETask(Server server, final Context context) {
        // GATT OBJECT TO CONNECT TO A GATT SERVER
        maxAttempt = 0;
        this.context = context;
        this.server = server;
        this.id = null;
        this.messageMap = new HashMap<>();
        receivedListeners = new ArrayList<>();
        internetListeners = new ArrayList<>();

        mGattCallback = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    isConnected = true;
                    Log.d(TAG, "OUD: Connected to GATT client. Attempting to start service discovery from " + gatt.getDevice().getName());
                    boolean res = gatt.discoverServices();
                    Log.d(TAG, "OUD: onConnectionStateChange: discover services :" + res);
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    isConnected = false;
                    Log.d(TAG, "OUD: " + "onConnectionStateChange: disco");
                }
                super.onConnectionStateChange(gatt, status, newState);
            }


            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                Log.d(TAG, "OUD: " + "GATT: " + gatt.toString());
                Log.d(TAG, "OUD: " + "I discovered a service");

                super.onServicesDiscovered(gatt, status);
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {

                    //Con questo if ho scritto la latitudine

                    if (characteristic.getUuid().equals(Constants.CharacteristicLatitudeUUID)) {

                        Log.d(TAG, "OUD: " + "I wrote a characteristic");
                        //Adesso riprendiamo il Service
                        // TODO: 06/03/2021 = null put just to make it work, to be controlled
                        BluetoothGattService service =null; //gatt.getService(Constants.LocationServiceUUID);
                        if (service == null) {
                            return;
                        }
                        Log.d(TAG, "OUD: service : " + service.getUuid().toString());
                        //Ora riprendiamo la caratteristica, come abbiamo fatto prima, ma per la longitudine
                        BluetoothGattCharacteristic charact = service.getCharacteristic(Constants.CharacteristicLongitudeUUID);
                        if (charact == null) {
                            return;
                        }
//                        longitude = c.catchLongitude().getBytes();
                        charact.setValue(longitude);
                        gatt.writeCharacteristic(charact);
                        longitude = null;
                    } else if (characteristic.getUuid().equals(Constants.CharacteristicLongitudeUUID)) {
                        Log.d(TAG, "OUD: " + "I wrote a characteristic");
                        //Adesso riprendiamo il Service
                        BluetoothGattService service =null; //gatt.getService(Constants.TimeServiceUUID);
                        if (service == null) {
                            return;
                        }
                        Log.d(TAG, "OUD: service : " + service.getUuid().toString());
                        //Ora riprendiamo la caratteristica, come abbiamo fatto prima, ma per il tempo
                        BluetoothGattCharacteristic charact = service.getCharacteristic(Constants.CharacteristicTimestampUUID);
                        if (charact == null) {
                            return;
                        }
                        Date currentTime = Calendar.getInstance().getTime();
                        DateFormat dateformat = new SimpleDateFormat("dd-mm-yyyy hh:mm:ss");
                        timestamp = dateformat.format(currentTime).getBytes();

                        charact.setValue(timestamp);
                        gatt.writeCharacteristic(charact);
                        timestamp = null;
                    } else {
                        Log.d(TAG, "OUD: " + "I wrote all the characteristics");
                    }
                }
                super.onCharacteristicWrite(gatt, characteristic, status);
            }

        };


    }

    public boolean IsDeviceConnected(){
        return this.isConnected;
    }

    /**
     *
     * @param latitude
     * @param longitude
     * @param timestamp
     */
    public void sendMessage(byte[] latitude, byte[] longitude, byte[] timestamp) {

        BluetoothGattService service = mGatt.getService(Constants.LocationServiceUUID);
        if (service == null) {
            return;
        }
        Log.d(TAG, "OUD: service : " + service.getUuid().toString());

        BluetoothGattCharacteristic characteristic = service.getCharacteristic(Constants.CharacteristicLatitudeUUID);
        if (characteristic == null) {
            return;
        }
        //Stiamo settando i valori che abbiamo sopra con quelli che abbiamo ottenuto con il sendmessage
        this.longitude = longitude;
        this.timestamp = timestamp;
        //Settiamo il valore della copia interna della sua caratteristica
        characteristic.setValue(latitude);
        //Stiamo effettivamente scrivendo i valori sul GATT dell'esp
        mGatt.writeCharacteristic(characteristic);
        
    }


    public void sendMessage(String latitude, String longitude, String timestamp) {

        sendMessage(latitude.getBytes(), longitude.getBytes(), timestamp.getBytes());
    }

    public void startClient() {
        this.mGatt = server.getBluetoothDevice().connectGatt(context, false, mGattCallback, BluetoothDevice.TRANSPORT_LE);
        server.setBluetoothGatt(this.mGatt);
        server.getBluetoothGatt().requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
        server.getBluetoothGatt().connect();
        
    }

    public void stopClient() {
                    mGatt.close();
                    mGatt = null;
    }
    
    public void setCallback(BluetoothGattCallback callback) {
        this.temporaryClient = true;
        this.mGattCallback = callback;
    }

    public void setJobDone() {
        jobDone = true;
        stopClient();
        if (this.onJobDoneListener != null) onJobDoneListener.OnJobDone();
    }

    public boolean getJobDone() {
        return jobDone;
    }

    public BluetoothGatt getmGatt() {
        return mGatt;
    }

    public void setOnJobDoneListener(Listeners.OnJobDoneListener l) {
        this.onJobDoneListener = l;
    }

    public void setOnConnectionLostListener(Listeners.OnConnectionLost l) {
        this.OnConnectionLostListener = l;
    }

    public void restartClient() {
        Log.d(TAG, "OUD: restartClient");
        stopClient();
        maxAttempt++;
        if (maxAttempt == Constants.MAX_ATTEMPTS_RETRY) {
            Log.d(TAG, "OUD: restartClient: mi arrendo, stop definitivo");
            return;
        }
        startClient();
    }

    public int getMaxAttempt() {
        return maxAttempt;
    }
}

